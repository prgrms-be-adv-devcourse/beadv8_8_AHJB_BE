package com.jjinmak.boundedcontext.market.auction.concurrency;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.app.support.AuctionRetrySupport;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.AuctionInvariants;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyReport;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner.Outcome;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner.Task;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.KafkaSendCounter;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
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

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static org.assertj.core.api.Assertions.assertThat;

// 영국식 동시 입찰 정합성 (CT-01~05, CT-09). 단언은 불변식만, 충돌·거부 수는 기록만 한다.
// 실행: ./gradlew :services:market-service:concurrencyTest -PlockLabel=v0-baseline --tests '*.concurrency.English*'
@Tag("concurrency")
class EnglishBidConcurrencyTest extends AuctionIntegrationTestSupport {

    // V4부터 (product_id, round) UNIQUE → 회차마다 다른 상품 id로 경매를 만든다
    private static final java.util.concurrent.atomic.AtomicLong PRODUCT_SEQ = new java.util.concurrent.atomic.AtomicLong(1_000_000L);

    private static final Long SELLER = 1L;
    private static final long START_PRICE = 10_000L;

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

    // 실제로 도는 종료·마감 임박 스케줄러가 건드리지 않도록 하루 뒤에 끝나는 경매
    private Long 경매(Long instantWinPrice) {
        LocalDateTime now = LocalDateTime.now();
        return auctionRepository.save(EnglishAuction.create(PRODUCT_SEQ.incrementAndGet(), SELLER, START_PRICE, instantWinPrice, 3_000L, "CONSOLE",
                now.plusDays(1), now.minusHours(1))).getId();
    }

    private static void 합치기(Map<String, Integer> total, List<Outcome> outcomes) {
        ConcurrencyRunner.countByCode(outcomes).forEach((k, v) -> total.merge(k, v, Integer::sum));
    }

    @Test
    void CT01_100명이_같은_금액으로_동시에_입찰하면_1명만_접수된다() {
        int rounds = 10, violated = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            Long id = 경매(null);
            List<Task> tasks = new ArrayList<>();
            for (long m = 2; m < 102; m++) {
                long member = m;
                tasks.add(new Task("bid", () -> retryBid(id, member, START_PRICE)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            합치기(total, outcomes);
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            if (!v.isEmpty() || ConcurrencyRunner.count(outcomes, "OK") != 1 || ConcurrencyRunner.hasUnexpectedError(outcomes)) {
                violated++;
                System.out.println("CT-01 위반: " + v + " " + ConcurrencyRunner.countByCode(outcomes));
            }
        }
        ConcurrencyReport.record("CT-01", "100명 같은 금액", rounds, violated, total.toString());
        assertThat(violated).isZero();
    }

    @Test
    void CT02_100명이_서로_다른_금액으로_동시에_입찰해도_불변식이_지켜진다() {
        int rounds = 10, violated = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            Long id = 경매(null);
            List<Task> tasks = new ArrayList<>();
            for (int k = 0; k < 100; k++) {
                long member = 2 + k;
                long price = START_PRICE + 1_000L * k;
                tasks.add(new Task("bid", () -> retryBid(id, member, price)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            합치기(total, outcomes);
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            if (!v.isEmpty() || ConcurrencyRunner.hasUnexpectedError(outcomes)) {
                violated++;
                System.out.println("CT-02 위반: " + v + " " + ConcurrencyRunner.countByCode(outcomes));
            }
        }
        ConcurrencyReport.record("CT-02", "100명 다른 금액", rounds, violated, total.toString());
        assertThat(violated).isZero();
    }

    @Test
    void CT03_같은_회원이_5번_동시에_입찰하면_최대_1건만_접수된다() {
        int rounds = 10, violated = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            Long id = 경매(null);
            List<Task> tasks = new ArrayList<>();
            for (int k = 0; k < 5; k++) {
                long price = START_PRICE + 1_000L * k;
                tasks.add(new Task("bid", () -> retryBid(id, 2L, price)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            합치기(total, outcomes);
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            if (!v.isEmpty() || ConcurrencyRunner.count(outcomes, "OK") > 1 || ConcurrencyRunner.hasUnexpectedError(outcomes)) {
                violated++;
            }
        }
        ConcurrencyReport.record("CT-03", "같은 회원 5번", rounds, violated, total.toString());
        assertThat(violated).isZero();
    }

    @Test
    void CT04_즉시낙찰_1건과_입찰_50건이_동시에_와도_낙찰은_정확하다() {
        int rounds = 10, violated = 0, instantWon = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            Long id = 경매(1_000_000L);
            List<Task> tasks = new ArrayList<>();
            tasks.add(new Task("instant", () -> retryInstantWin(id, 999L)));
            for (int k = 0; k < 50; k++) {
                long member = 2 + k;
                long price = START_PRICE + 1_000L * k;
                tasks.add(new Task("bid", () -> retryBid(id, member, price)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            합치기(total, outcomes);
            if (ConcurrencyRunner.count(outcomes, "instant", "OK") == 1) {
                instantWon++;
            }
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            if (!v.isEmpty() || ConcurrencyRunner.hasUnexpectedError(outcomes)) {
                violated++;
                System.out.println("CT-04 위반: " + v);
            }
        }
        ConcurrencyReport.record("CT-04", "즉시낙찰 1 vs 입찰 50", rounds, violated,
                "즉시낙찰 성공 " + instantWon + "/" + rounds + " " + total);
        assertThat(violated).isZero();
    }

    @Test
    void CT05_즉시낙찰_20건과_입찰_20건이_동시에_와도_낙찰과_이벤트는_1번이다() {
        int rounds = 10, violated = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            Long id = 경매(1_000_000L);
            List<Task> tasks = new ArrayList<>();
            for (int k = 0; k < 20; k++) {
                long instantMember = 500 + k;
                long member = 2 + k;
                long price = START_PRICE + 1_000L * k;
                tasks.add(new Task("instant", () -> retryInstantWin(id, instantMember)));
                tasks.add(new Task("bid", () -> retryBid(id, member, price)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            합치기(total, outcomes);
            EnglishAuction auction = (EnglishAuction) auctionRepository.findById(id).orElseThrow();
            long wonEvents = KafkaSendCounter.count(kafkaTemplate, AUCTION_WON, id);
            long expectedEvents = auction.getStatus() == AuctionStatus.WON ? 1 : 0;
            long instantOk = ConcurrencyRunner.count(outcomes, "instant", "OK");
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            if (!v.isEmpty() || wonEvents != expectedEvents || instantOk > 1 || ConcurrencyRunner.hasUnexpectedError(outcomes)) {
                violated++;
                System.out.println("CT-05 위반: " + v + " 낙찰 이벤트 " + wonEvents + " 즉시낙찰 성공 " + instantOk);
            }
        }
        ConcurrencyReport.record("CT-05", "즉시낙찰 20 + 입찰 20", rounds, violated, total.toString());
        assertThat(violated).isZero();
    }

    // 1만원 입찰과 1만5천원 입찰이 동시에 오면, 1만5천원은 어느 순서로 처리돼도 유효하다.
    // 1만5천원이 CONFLICT로 거절되면 "유효한 입찰 거부". 재시도 전(v0)에는 발생, 재시도 후(v1) 목표 0.
    @Test
    void CT09_유효한_입찰이_락_충돌로_거부되는_비율을_잰다() {
        int rounds = 50, violated = 0, rejected = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            Long id = 경매(null);
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(List.of(
                    new Task("10000", () -> retryBid(id, 2L, 10_000L)),
                    new Task("15000", () -> retryBid(id, 3L, 15_000L))));
            합치기(total, outcomes);
            if (ConcurrencyRunner.count(outcomes, "15000", "OK") == 0) {
                rejected++;
            }
            List<String> v = AuctionInvariants.checkEnglish(auctionRepository, bidRepository, id);
            if (!v.isEmpty() || ConcurrencyRunner.hasUnexpectedError(outcomes)) {
                violated++;
            }
        }
        ConcurrencyReport.record("CT-09", "1만원 vs 1만5천원 (유효 입찰 거부율)", rounds, violated,
                String.format("1만5천원 거부 %d/%d (%.0f%%) %s", rejected, rounds, rejected * 100.0 / rounds, total));
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
