package com.jjinmak.boundedcontext.market.auction.concurrency;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.app.support.AuctionRetrySupport;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.AuctionInvariants;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyReport;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner.Outcome;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner.Task;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.OutboxEventCounter;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.market.auction.domain.Bid;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import com.jjinmak.boundedcontext.market.auction.out.BidRepository;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_FAILED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static org.assertj.core.api.Assertions.assertThat;

// 종료 처리 vs 막판 입찰·즉시낙찰 (CT-06, CT-08)
// 종료 처리는 "종료 시각이 지났다"고 보는 now(endAt + 1초)를 넘기고, 입찰은 실제 현재 시각(종료 전)으로 들어온다.
// → 둘 다 자기 기준으로는 유효해서 같은 경매 행을 두고 경합한다. 단언: 불변식 + 결과 이벤트 정확히 1번
@Tag("concurrency")
class CloseRaceConcurrencyTest extends AuctionIntegrationTestSupport {

    // V4부터 (product_id, round) UNIQUE → 회차마다 다른 상품 id로 경매를 만든다
    private static final java.util.concurrent.atomic.AtomicLong PRODUCT_SEQ = new java.util.concurrent.atomic.AtomicLong(1_000_000L);

    @Autowired AuctionFacade auctionFacade;
    @Autowired AuctionRetrySupport auctionRetrySupport;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
    }

    @AfterAll
    static void flush() {
        ConcurrencyReport.flush();
    }

    private EnglishAuction 경매(Long instantWinPrice) {
        LocalDateTime now = LocalDateTime.now();
        EnglishAuction auction = EnglishAuction.create(PRODUCT_SEQ.incrementAndGet(), 1L, 10_000L, instantWinPrice, 3_000L, "CONSOLE",
                now.plusDays(1), now.minusHours(1));
        auction.updateHighestBid(900L, 10_000L);   // 입찰 1건 있는 상태로 시작 (낙찰 경로)
        EnglishAuction saved = auctionRepository.save(auction);
        bidRepository.save(new Bid(saved.getId(), 900L, 10_000L, now.minusMinutes(1)));
        return saved;
    }

    private long 결과_이벤트(Long id) {
        return OutboxEventCounter.count(jdbcTemplate, AUCTION_WON, id) + OutboxEventCounter.count(jdbcTemplate, AUCTION_FAILED, id);
    }

    @Test
    void CT06_종료_처리와_막판_입찰_20건이_경합해도_불변식과_결과_이벤트_1번이_지켜진다() {
        int rounds = 10, violated = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            EnglishAuction auction = 경매(null);
            Long id = auction.getId();
            LocalDateTime closeNow = auction.getEndAt().plusSeconds(1);
            List<Task> tasks = new ArrayList<>();
            tasks.add(new Task("close", () -> auctionFacade.closeAuction(id, closeNow)));
            for (int k = 0; k < 20; k++) {
                long member = 2 + k;
                long price = 11_000L + 1_000L * k;
                tasks.add(new Task("bid", () -> retryBid(id, member, price)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            ConcurrencyRunner.countByCode(outcomes).forEach((k, v) -> total.merge(k, v, Integer::sum));
            // 종료가 충돌로 밀렸으면 다음 주기처럼 한 번 더 종료한다
            if (auctionRepository.findById(id).orElseThrow().getStatus() == AuctionStatus.IN_PROGRESS) {
                auctionFacade.closeAuction(id, closeNow);
            }
            EnglishAuction after = (EnglishAuction) auctionRepository.findById(id).orElseThrow();
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            long events = 결과_이벤트(id);
            if (!v.isEmpty() || after.getStatus() == AuctionStatus.IN_PROGRESS || events != 1
                    || ConcurrencyRunner.hasUnexpectedError(outcomes)) {
                violated++;
                System.out.println("CT-06 위반: " + v + " 상태 " + after.getStatus() + " 결과 이벤트 " + events);
            }
        }
        ConcurrencyReport.record("CT-06", "종료 vs 막판 입찰 20", rounds, violated, total.toString());
        assertThat(violated).isZero();
    }

    @Test
    void CT08_종료_처리와_즉시낙찰_5건이_경합해도_낙찰은_정확히_1번이다() {
        int rounds = 10, violated = 0, closeWon = 0, instantWon = 0;
        for (int r = 0; r < rounds; r++) {
            EnglishAuction auction = 경매(1_000_000L);
            Long id = auction.getId();
            LocalDateTime closeNow = auction.getEndAt().plusSeconds(1);
            List<Task> tasks = new ArrayList<>();
            tasks.add(new Task("close", () -> auctionFacade.closeAuction(id, closeNow)));
            for (int k = 0; k < 5; k++) {
                long member = 2 + k;
                tasks.add(new Task("instant", () -> retryInstantWin(id, member)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            long instantOk = ConcurrencyRunner.count(outcomes, "instant", "OK");
            instantWon += instantOk > 0 ? 1 : 0;
            EnglishAuction after = (EnglishAuction) auctionRepository.findById(id).orElseThrow();
            if (instantOk == 0 && after.getStatus() == AuctionStatus.WON) {
                closeWon++;
            }
            long wonEvents = OutboxEventCounter.count(jdbcTemplate, AUCTION_WON, id);
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            boolean ok = v.isEmpty() && instantOk <= 1 && after.getStatus() == AuctionStatus.WON && wonEvents == 1
                    && !ConcurrencyRunner.hasUnexpectedError(outcomes);
            if (!ok) {
                violated++;
                System.out.println("CT-08 위반: " + v + " 즉시낙찰 성공 " + instantOk + " 상태 " + after.getStatus() + " 낙찰 이벤트 " + wonEvents);
            }
        }
        ConcurrencyReport.record("CT-08", "종료 vs 즉시낙찰 5", rounds, violated,
                "종료 승 " + closeWon + " / 즉시낙찰 승 " + instantWon);
        assertThat(violated).isZero();
    }

    // 컨트롤러와 같은 경로로 호출한다: 낙관적 락 충돌이면 새 트랜잭션에서 재검증 (#514, 최대 3회, 소진 시 AUCTION008)
    private void retryBid(Long auctionId, Long memberId, Long price) {
        auctionRetrySupport.execute(AuctionRetrySupport.Operation.BID, () -> auctionFacade.bid(auctionId, memberId, price));
    }

    private void retryInstantWin(Long auctionId, Long memberId) {
        auctionRetrySupport.execute(AuctionRetrySupport.Operation.INSTANT_WIN, () -> auctionFacade.instantWin(auctionId, memberId));
    }
}
