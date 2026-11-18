package com.jjinmak.ai.recommend.app;

import com.jjinmak.ai.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.ai.recommend.domain.RecommendAuction;
import com.jjinmak.ai.recommend.domain.RecommendMember;
import com.jjinmak.ai.recommend.in.RecommendKafkaListener;
import com.jjinmak.ai.recommend.out.repository.InteractionLogRepository;
import com.jjinmak.ai.recommend.out.repository.InteractionRepository;
import com.jjinmak.ai.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.ai.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.ai.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.ai.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.ai.recommend.support.RecommendIntegrationTestSupport;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.jjinmak.ai.recommend.domain.AuctionStatus.ENDED;
import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendAuctionConcurrencyTest extends RecommendIntegrationTestSupport {

    static final long AUCTION_ID = 100L;
    static final long PRODUCT_ID = 55L;
    static final long SELLER_ID = 9L;
    static final long BIDDER_ID = 7L;
    static final long WINNER_ID = 8L;
    static final long START_PRICE = 10000L;
    static final long FIRST_BID_PRICE = 21000L;
    static final long INSTANT_WIN_PRICE = 50000L;

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    RecommendFacade recommendFacade;

    @Autowired
    RecommendAuctionRepository recommendAuctionRepository;

    @Autowired
    RecommendProductRepository recommendProductRepository;

    @Autowired
    RecommendMemberRepository recommendMemberRepository;

    @Autowired
    InteractionRepository interactionRepository;

    @Autowired
    InteractionLogRepository interactionLogRepository;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    ExecutorService executor;
    LocalDateTime now;
    LocalDateTime endAt;

    @BeforeEach
    void startAuctionWithOneBid() {
        executor = Executors.newFixedThreadPool(2);
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        endAt = now.plusDays(2);
        recommendKafkaListener.handle(new ProductChangedEvent(PRODUCT_ID, CREATED, 0L, SELLER_ID, "듀얼센스", "ACCESSORY", "SONY", null,
                START_PRICE, INSTANT_WIN_PRICE, now.minusHours(2), null, now.minusHours(3)));
        recommendMemberRepository.save(new RecommendMember(BIDDER_ID, UUID.randomUUID(), "회원" + BIDDER_ID));
        recommendMemberRepository.save(new RecommendMember(WINNER_ID, UUID.randomUUID(), "회원" + WINNER_ID));
        recommendKafkaListener.handle(new AuctionStartedEvent(AUCTION_ID, PRODUCT_ID, START_PRICE, INSTANT_WIN_PRICE,
                now.minusHours(1), endAt, "ENGLISH"));
        recommendKafkaListener.handle(new AuctionBidSucceedEvent(1L, AUCTION_ID, PRODUCT_ID, BIDDER_ID,
                FIRST_BID_PRICE, 1, endAt, now.minusMinutes(30), null));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    @Test
    @DisplayName("즉시 낙찰의 입찰 처리가 경매를 잠근 동안 들어온 낙찰 처리는 입찰 커밋을 기다렸다가 경매를 끝내고, 입찰 수와 BID·WIN 상호작용이 모두 남는다")
    void instantWin_bidLocksFirst_wonWaitsAndEndsAuction() throws Exception {
        boolean waited = handleWhileLocked(
                () -> recommendKafkaListener.handle(instantWinBid()),
                () -> recommendKafkaListener.handle(instantWinWon()));

        assertThat(waited).isTrue();
        assertEndedByInstantWin();
    }

    @Test
    @DisplayName("즉시 낙찰의 낙찰 처리가 경매를 잠근 동안 들어온 입찰 처리는 낙찰 커밋을 기다렸다가 입찰만 반영하고, 경매는 끝난 상태로 남는다")
    void instantWin_wonLocksFirst_bidWaitsAndKeepsEnded() throws Exception {
        boolean waited = handleWhileLocked(
                () -> recommendKafkaListener.handle(instantWinWon()),
                () -> recommendKafkaListener.handle(instantWinBid()));

        assertThat(waited).isTrue();
        assertEndedByInstantWin();
    }

    @Test
    @DisplayName("낙찰 처리가 경매를 잠근 동안 들어온 미결제 유찰 처리는 낙찰 커밋을 기다렸다가 끝난 경매로 보고, 낙찰 시각과 낙찰가를 그대로 둔다")
    void wonLocksFirst_unpaidFailedWaitsAndKeepsWin() throws Exception {
        boolean waited = handleWhileLocked(
                () -> recommendKafkaListener.handle(instantWinWon()),
                () -> recommendKafkaListener.handle(new AuctionFailedEvent(AUCTION_ID, PRODUCT_ID, "UNPAID", now.plusHours(25), SELLER_ID)));

        assertThat(waited).isTrue();
        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(now);
        assertThat(auction.getCurrentPrice()).isEqualTo(INSTANT_WIN_PRICE);
    }

    private boolean handleWhileLocked(Runnable lockingHandler, Runnable waitingHandler) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> locking = executor.submit(() -> transaction.executeWithoutResult(status -> {
            lockingHandler.run();
            locked.countDown();
            awaitRelease(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        Future<?> waiting = executor.submit(waitingHandler);
        boolean waited = someoneWaitsForLock();
        release.countDown();
        locking.get(10, TimeUnit.SECONDS);
        waiting.get(10, TimeUnit.SECONDS);
        return waited;
    }

    private boolean someoneWaitsForLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbcTemplate.queryForObject(
                    "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                    Long.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertEndedByInstantWin() {
        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(now);
        assertThat(auction.getCurrentPrice()).isEqualTo(INSTANT_WIN_PRICE);
        assertThat(auction.getBidCount()).isEqualTo(2L);
        assertThat(interactionTypes(WINNER_ID)).containsExactlyInAnyOrder("BID", "WIN");
        assertThat(popularAuctionIds()).doesNotContain(AUCTION_ID);
    }

    private RecommendAuction auction() {
        return recommendAuctionRepository.findById(AUCTION_ID).orElseThrow();
    }

    private List<String> interactionTypes(long memberId) {
        return jdbcTemplate.queryForList("select type from interaction where member_id = ? order by id", String.class, memberId);
    }

    private List<Long> popularAuctionIds() {
        return recommendFacade.readPopularAuctionList().stream().map(AuctionResponseDto::auctionId).toList();
    }

    private AuctionBidSucceedEvent instantWinBid() {
        return new AuctionBidSucceedEvent(2L, AUCTION_ID, PRODUCT_ID, WINNER_ID, INSTANT_WIN_PRICE, 2, endAt, now, BIDDER_ID);
    }

    private AuctionWonEvent instantWinWon() {
        return new AuctionWonEvent(AUCTION_ID, PRODUCT_ID, WINNER_ID, SELLER_ID, INSTANT_WIN_PRICE, 3000L, now);
    }
}
