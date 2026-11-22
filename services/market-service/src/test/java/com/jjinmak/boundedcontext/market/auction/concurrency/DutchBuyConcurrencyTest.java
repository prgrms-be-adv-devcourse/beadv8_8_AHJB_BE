package com.jjinmak.boundedcontext.market.auction.concurrency;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyReport;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner.Outcome;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.ConcurrencyRunner.Task;
import com.jjinmak.boundedcontext.market.auction.concurrency.support.OutboxEventCounter;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.market.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import com.jjinmak.boundedcontext.market.auction.out.BidRepository;
import com.jjinmak.boundedcontext.market.auction.support.AuctionIntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_FAILED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static org.assertj.core.api.Assertions.assertThat;

// 하향식 동시 구매 (CT-D1, CT-D2). 낙관적 락 충돌(CONFLICT)은 API에서 AUCTION014로 바뀐다.
@Tag("concurrency")
class DutchBuyConcurrencyTest extends AuctionIntegrationTestSupport {

    // V4부터 (product_id, round) UNIQUE → 회차마다 다른 상품 id로 경매를 만든다
    private static final java.util.concurrent.atomic.AtomicLong PRODUCT_SEQ = new java.util.concurrent.atomic.AtomicLong(1_000_000L);

    // 시작가 30만, 최저가 15만, 2%, 120초 → 6,000원씩, 마지막 단계 25
    private static final long START = 300_000L, FLOOR = 150_000L;
    private static final int RATE = 2, INTERVAL = 120, LAST_STEP = 25;

    @Autowired AuctionFacade auctionFacade;
    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;

    @AfterEach
    void tearDown() {
        auctionRepository.deleteAllInBatch();
    }

    @AfterAll
    static void flush() {
        ConcurrencyReport.flush();
    }

    private DutchAuction 경매(LocalDateTime startAt) {
        return auctionRepository.save(DutchAuction.create(PRODUCT_SEQ.incrementAndGet(), 1L, START, FLOOR, RATE, INTERVAL, 3_000L, "CONSOLE", startAt));
    }

    private boolean 입찰_행_없음(Long id) {
        return bidRepository.findByAuctionIdOrderByIdDesc(id, PageRequest.of(0, 1)).isEmpty();
    }

    @Test
    void CTD1_100명이_같은_가격에_동시에_구매하면_낙찰은_정확히_1건이다() {
        int rounds = 10, violated = 0;
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            Long id = 경매(LocalDateTime.now().minusSeconds(1)).getId();   // 지금 가격 = 시작가 (120초 동안)
            List<Task> tasks = new ArrayList<>();
            for (long m = 2; m < 102; m++) {
                long member = m;
                tasks.add(new Task("buy", () -> auctionFacade.dutchBuy(id, member, START)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            ConcurrencyRunner.countByCode(outcomes).forEach((k, v) -> total.merge(k, v, Integer::sum));
            DutchAuction after = (DutchAuction) auctionRepository.findById(id).orElseThrow();
            long ok = ConcurrencyRunner.count(outcomes, "OK");
            long wonEvents = OutboxEventCounter.count(jdbcTemplate, AUCTION_WON, id);
            boolean pass = ok == 1 && after.getStatus() == AuctionStatus.WON && after.getWinnerId() != null
                    && after.getWinningPrice() == START && wonEvents == 1 && 입찰_행_없음(id)
                    && !ConcurrencyRunner.hasUnexpectedError(outcomes);
            if (!pass) {
                violated++;
                System.out.println("CT-D1 위반: 성공 " + ok + " 상태 " + after.getStatus() + " 이벤트 " + wonEvents);
            }
        }
        ConcurrencyReport.record("CT-D1", "하향식 100명 동시 구매", rounds, violated, total.toString());
        assertThat(violated).isZero();
    }

    // 마지막 가격 단계에서 구매 20명과 유찰 처리(종료 스케줄러가 하는 일)를 같은 순간 실행한다.
    // 구매는 실제 현재 시각(종료 전)으로, 유찰 처리는 종료 시각이 지났다고 보는 now(endAt + 1초)로 들어와
    // 둘 다 자기 기준으로는 유효하다 → 같은 경매 행을 두고 경합. 기대: 낙찰·유찰 중 하나만, 결과 이벤트 1번
    @Test
    void CTD2_마지막_단계_구매와_유찰_처리가_경합해도_결과는_하나다() {
        int rounds = 10, violated = 0, won = 0, failed = 0;
        Duration life = Duration.ofSeconds((long) INTERVAL * (LAST_STEP + 1));
        Map<String, Integer> total = new TreeMap<>();
        for (int r = 0; r < rounds; r++) {
            LocalDateTime endAt = LocalDateTime.now().plusSeconds(60);   // 지금은 마지막 단계(120초) 안
            DutchAuction auction = 경매(endAt.minus(life));
            Long id = auction.getId();
            LocalDateTime closeNow = auction.getEndAt().plusSeconds(1);
            List<Task> tasks = new ArrayList<>();
            tasks.add(new Task("close", () -> auctionFacade.closeAuction(id, closeNow)));
            for (long m = 2; m < 22; m++) {
                long member = m;
                tasks.add(new Task("buy", () -> auctionFacade.dutchBuy(id, member, START)));
            }
            List<Outcome> outcomes = ConcurrencyRunner.runTogether(tasks);
            ConcurrencyRunner.countByCode(outcomes).forEach((k, v) -> total.merge(k, v, Integer::sum));
            DutchAuction after = (DutchAuction) auctionRepository.findById(id).orElseThrow();
            long wonEvents = OutboxEventCounter.count(jdbcTemplate, AUCTION_WON, id);
            long failedEvents = OutboxEventCounter.count(jdbcTemplate, AUCTION_FAILED, id);
            if (after.getStatus() == AuctionStatus.WON) won++;
            if (after.getStatus() == AuctionStatus.FAILED) failed++;
            long buyOk = ConcurrencyRunner.count(outcomes, "buy", "OK");
            boolean pass = (after.getStatus() == AuctionStatus.WON || after.getStatus() == AuctionStatus.FAILED)
                    && wonEvents + failedEvents == 1
                    && buyOk == (after.getStatus() == AuctionStatus.WON ? 1 : 0)
                    && 입찰_행_없음(id)
                    && !ConcurrencyRunner.hasUnexpectedError(outcomes);
            if (!pass) {
                violated++;
                System.out.println("CT-D2 위반: 상태 " + after.getStatus() + " 낙찰 이벤트 " + wonEvents + " 유찰 이벤트 " + failedEvents
                        + " " + ConcurrencyRunner.countByCode(outcomes));
            }
        }
        ConcurrencyReport.record("CT-D2", "하향식 마지막 단계 구매 20 vs 유찰 처리", rounds, violated,
                "낙찰 " + won + " / 유찰 " + failed + " " + total);
        assertThat(violated).isZero();
    }
}
