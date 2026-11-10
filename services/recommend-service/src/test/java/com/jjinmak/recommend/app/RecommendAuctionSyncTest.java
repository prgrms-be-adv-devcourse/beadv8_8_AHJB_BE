package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.domain.RecommendAuction;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.InteractionLogRepository;
import com.jjinmak.recommend.out.InteractionRepository;
import com.jjinmak.recommend.out.RecommendAuctionRepository;
import com.jjinmak.recommend.out.RecommendMemberRepository;
import com.jjinmak.recommend.out.RecommendProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.recommend.domain.AuctionStatus.ACTIVE;
import static com.jjinmak.recommend.domain.AuctionStatus.ENDED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RecommendAuctionSyncTest {

    static final long AUCTION_ID = 100L;
    static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 10, 10, 0);
    static final LocalDateTime END_AT = START_AT.plusDays(3);
    static final LocalDateTime WIN_AT = START_AT.plusDays(1);
    static final LocalDateTime UNPAID_AT = WIN_AT.plusHours(25);

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

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

    @BeforeEach
    void createProductAndWinnerCopies() {
        recommendKafkaListener.handle(new ProductChangedEvent(55L, CREATED, 0L, 9L, "듀얼센스", "ACCESSORY", "SONY", null,
                10000L, 50000L, START_AT, null, START_AT.minusDays(1)));
        recommendMemberRepository.save(new RecommendMember(7L, UUID.randomUUID(), "낙찰자"));
    }

    @AfterEach
    void tearDown() {
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    @Test
    @DisplayName("경매 시작 이벤트를 받으면 경매 id를 PK로 진행 중(ACTIVE)인 복사본을 만든다. 현재가는 시작가, 입찰 수는 0이다")
    void started_savesActiveAuction() {
        recommendKafkaListener.handle(started());

        RecommendAuction auction = auction();
        assertThat(auction.getId()).isEqualTo(AUCTION_ID);
        assertThat(auction.getProductId()).isEqualTo(55L);
        assertThat(auction.getInstantWinPrice()).isEqualTo(50000L);
        assertThat(auction.getCurrentPrice()).isEqualTo(10000L);
        assertThat(auction.getBidCount()).isZero();
        assertThat(auction.getStartAt()).isEqualTo(START_AT);
        assertThat(auction.getEndAt()).isEqualTo(END_AT);
        assertThat(auction.getEndedAt()).isNull();
        assertThat(auction.getStatus()).isEqualTo(ACTIVE);
    }

    @Test
    @DisplayName("낙찰 이벤트를 받으면 끝난(ENDED) 경매가 되고, 종료 시각은 낙찰 시각, 현재가는 낙찰가가 된다")
    void won_endsAuctionAtWinTime() {
        recommendKafkaListener.handle(started());

        recommendKafkaListener.handle(won());

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(WIN_AT);
        assertThat(auction.getCurrentPrice()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("입찰 없이 유찰되면 끝난 경매가 되고, 종료 시각은 유찰 시각이다")
    void failedWithoutBids_endsAuctionAtFailTime() {
        recommendKafkaListener.handle(started());

        recommendKafkaListener.handle(failed("NO_BID", END_AT));

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(END_AT);
    }

    @Test
    @DisplayName("낙찰 뒤 미결제로 유찰 이벤트가 와도 종료 시각은 낙찰 시각으로 남는다")
    void failedAfterWon_keepsWinTime() {
        recommendKafkaListener.handle(started());
        recommendKafkaListener.handle(won());

        recommendKafkaListener.handle(failed("UNPAID", UNPAID_AT));

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(WIN_AT);
        assertThat(auction.getCurrentPrice()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("미결제 유찰이 낙찰보다 먼저 처리되어도 종료 시각은 낙찰 시각이 된다")
    void failedBeforeWon_endsAtWinTime() {
        recommendKafkaListener.handle(started());
        recommendKafkaListener.handle(failed("UNPAID", UNPAID_AT));

        recommendKafkaListener.handle(won());

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(WIN_AT);
    }

    @Test
    @DisplayName("같은 낙찰 이벤트를 다시 받아도 결과가 같다")
    void redeliveredWon_keepsResult() {
        recommendKafkaListener.handle(started());
        recommendKafkaListener.handle(won());

        recommendKafkaListener.handle(won());

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(WIN_AT);
        assertThat(auction.getCurrentPrice()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("같은 유찰 이벤트를 다시 받아도 결과가 같다")
    void redeliveredFailed_keepsResult() {
        recommendKafkaListener.handle(started());
        recommendKafkaListener.handle(failed("NO_BID", END_AT));

        recommendKafkaListener.handle(failed("NO_BID", END_AT));

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(END_AT);
    }

    @Test
    @DisplayName("복사본이 없는 경매의 낙찰 이벤트는 AUCTION_NOT_FOUND로 실패해 재시도에 맡기고, 복사본을 만들지 않는다")
    void wonWithoutAuction_throwsAuctionNotFound() {
        assertThatThrownBy(() -> recommendKafkaListener.handle(won()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AUCTION_NOT_FOUND);
        assertThat(recommendAuctionRepository.count()).isZero();
    }

    @Test
    @DisplayName("복사본이 없는 경매의 유찰 이벤트는 AUCTION_NOT_FOUND로 실패해 재시도에 맡기고, 복사본을 만들지 않는다")
    void failedWithoutAuction_throwsAuctionNotFound() {
        assertThatThrownBy(() -> recommendKafkaListener.handle(failed("NO_BID", END_AT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AUCTION_NOT_FOUND);
        assertThat(recommendAuctionRepository.count()).isZero();
    }

    private RecommendAuction auction() {
        return recommendAuctionRepository.findById(AUCTION_ID).orElseThrow();
    }

    private static AuctionStartedEvent started() {
        return new AuctionStartedEvent(AUCTION_ID, 55L, 10000L, 50000L, START_AT, END_AT);
    }

    private static AuctionWonEvent won() {
        return new AuctionWonEvent(AUCTION_ID, 55L, 7L, 9L, 30000L, 3000L, WIN_AT);
    }

    private static AuctionFailedEvent failed(String reason, LocalDateTime failedAt) {
        return new AuctionFailedEvent(AUCTION_ID, 55L, reason, failedAt);
    }
}
