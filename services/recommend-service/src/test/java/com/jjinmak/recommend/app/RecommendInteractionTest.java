package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.domain.Interaction;
import com.jjinmak.recommend.domain.InteractionLog;
import com.jjinmak.recommend.domain.InteractionType;
import com.jjinmak.recommend.domain.RecommendAuction;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.domain.RecommendProduct;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;
import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.recommend.domain.AuctionStatus.ACTIVE;
import static com.jjinmak.recommend.domain.AuctionStatus.ENDED;
import static com.jjinmak.recommend.domain.InteractionType.BID;
import static com.jjinmak.recommend.domain.InteractionType.WIN;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RecommendInteractionTest {

    static final long AUCTION_ID = 100L;
    static final long PRODUCT_ID = 55L;
    static final long SELLER_ID = 9L;
    static final long BIDDER_ID = 7L;
    static final long OTHER_BIDDER_ID = 8L;
    static final long UNKNOWN_ID = 999L;
    static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 10, 10, 0);
    static final LocalDateTime END_AT = START_AT.plusDays(3);
    static final LocalDateTime BID_AT = START_AT.plusHours(1);
    static final LocalDateTime WIN_AT = END_AT.plusMinutes(1);

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

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createCopies() {
        recommendKafkaListener.handle(productCreated());
        recommendKafkaListener.handle(started());
        recommendMemberRepository.save(memberCopy(BIDDER_ID));
        recommendMemberRepository.save(memberCopy(OTHER_BIDDER_ID));
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
    @DisplayName("낙찰 이벤트를 받으면 낙찰자의 WIN 상호작용과 로그를 낙찰 시각으로 저장한다")
    void won_savesWinInteractionAndLog() {
        recommendKafkaListener.handle(won(BIDDER_ID, WIN_AT));

        assertThat(interactions()).containsExactly(interactionRow(BIDDER_ID, WIN, WIN_AT));
        assertThat(logs()).containsExactly(logRow(BIDDER_ID, WIN, WIN_AT));
    }

    @Test
    @DisplayName("같은 낙찰 이벤트를 다시 받아도 실패하지 않고 상호작용과 로그는 한 행씩이다")
    void redeliveredWon_keepsOneInteractionAndLog() {
        recommendKafkaListener.handle(won(BIDDER_ID, WIN_AT));

        assertThatCode(() -> recommendKafkaListener.handle(won(BIDDER_ID, WIN_AT))).doesNotThrowAnyException();

        assertThat(interactions()).containsExactly(interactionRow(BIDDER_ID, WIN, WIN_AT));
        assertThat(logs()).containsExactly(logRow(BIDDER_ID, WIN, WIN_AT));
    }

    @Test
    @DisplayName("입찰한 회원이 낙찰받으면 BID와 따로 WIN 상호작용과 로그가 생긴다")
    void wonAfterBid_addsWinBesideBid() {
        recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT));

        recommendKafkaListener.handle(won(BIDDER_ID, WIN_AT));

        assertThat(interactions()).containsExactly(
                interactionRow(BIDDER_ID, BID, BID_AT),
                interactionRow(BIDDER_ID, WIN, WIN_AT));
        assertThat(logs()).containsExactly(
                logRow(BIDDER_ID, BID, BID_AT),
                logRow(BIDDER_ID, WIN, WIN_AT));
    }

    @Test
    @DisplayName("낙찰자의 회원 복사본이 없으면 USER_NOT_FOUND로 실패하고, 같은 트랜잭션의 경매 종료도 반영되지 않는다")
    void wonWithoutMemberCopy_throwsUserNotFoundAndKeepsAuctionActive() {
        assertThatThrownBy(() -> recommendKafkaListener.handle(won(UNKNOWN_ID, WIN_AT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ACTIVE);
        assertThat(auction.getEndedAt()).isNull();
        assertThat(interactions()).isEmpty();
        assertThat(logs()).isEmpty();
    }

    @Test
    @DisplayName("낙찰 상품의 복사본이 없으면 PRODUCT_NOT_FOUND로 실패하고, 같은 트랜잭션의 경매 종료도 반영되지 않는다")
    void wonWithoutProductCopy_throwsProductNotFoundAndKeepsAuctionActive() {
        AuctionWonEvent event = new AuctionWonEvent(AUCTION_ID, UNKNOWN_ID, BIDDER_ID, SELLER_ID, 30000L, 3000L, WIN_AT);

        assertThatThrownBy(() -> recommendKafkaListener.handle(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PRODUCT_NOT_FOUND);

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ACTIVE);
        assertThat(auction.getEndedAt()).isNull();
        assertThat(interactions()).isEmpty();
        assertThat(logs()).isEmpty();
    }

    @Test
    @DisplayName("입찰 이벤트를 받으면 입찰자의 BID 상호작용과 로그를 입찰 시각으로 저장한다")
    void bid_savesBidInteractionAndLog() {
        recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT));

        assertThat(interactions()).containsExactly(interactionRow(BIDDER_ID, BID, BID_AT));
        assertThat(logs()).containsExactly(logRow(BIDDER_ID, BID, BID_AT));
    }

    @Test
    @DisplayName("같은 회원이 다시 입찰하면 상호작용은 회원마다 한 행으로 마지막 입찰 시각만 바뀌고, 로그는 입찰마다 쌓인다")
    void repeatedBids_keepOneInteractionPerMemberAndLogEachBid() {
        recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT));
        recommendKafkaListener.handle(bid(2L, OTHER_BIDDER_ID, BID_AT.plusMinutes(5), END_AT));

        recommendKafkaListener.handle(bid(3L, BIDDER_ID, BID_AT.plusMinutes(10), END_AT));

        assertThat(interactions()).containsExactly(
                interactionRow(BIDDER_ID, BID, BID_AT.plusMinutes(10)),
                interactionRow(OTHER_BIDDER_ID, BID, BID_AT.plusMinutes(5)));
        assertThat(logs()).containsExactly(
                logRow(BIDDER_ID, BID, BID_AT),
                logRow(OTHER_BIDDER_ID, BID, BID_AT.plusMinutes(5)),
                logRow(BIDDER_ID, BID, BID_AT.plusMinutes(10)));
    }

    @Test
    @DisplayName("같은 입찰 이벤트를 다시 받아도 실패하지 않고 상호작용과 로그가 그대로다")
    void redeliveredBid_keepsResult() {
        recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT));

        assertThatCode(() -> recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT))).doesNotThrowAnyException();

        assertThat(interactions()).containsExactly(interactionRow(BIDDER_ID, BID, BID_AT));
        assertThat(logs()).containsExactly(logRow(BIDDER_ID, BID, BID_AT));
    }

    @Test
    @DisplayName("예전 입찰 이벤트가 늦게 처리되어도 마지막 입찰 시각은 줄지 않고, 로그에는 그 입찰이 더해진다")
    void olderBidProcessedLater_keepsLatestTimeAndAddsLog() {
        recommendKafkaListener.handle(bid(2L, BIDDER_ID, BID_AT.plusMinutes(10), END_AT));

        recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT));

        assertThat(interactions()).containsExactly(interactionRow(BIDDER_ID, BID, BID_AT.plusMinutes(10)));
        assertThat(logs()).containsExactly(
                logRow(BIDDER_ID, BID, BID_AT.plusMinutes(10)),
                logRow(BIDDER_ID, BID, BID_AT));
    }

    @Test
    @DisplayName("입찰 이벤트의 종료 시각이 더 늦으면 경매 복사본의 종료 예정 시각을 늘리고, 실제 종료 시각은 비워 둔다")
    void bidWithLaterEndAt_extendsAuctionEnd() {
        recommendKafkaListener.handle(bid(1L, BIDDER_ID, END_AT.minusMinutes(3), END_AT.plusMinutes(2)));

        RecommendAuction auction = auction();
        assertThat(auction.getEndAt()).isEqualTo(END_AT.plusMinutes(2));
        assertThat(auction.getEndedAt()).isNull();
        assertThat(auction.getStatus()).isEqualTo(ACTIVE);
    }

    @Test
    @DisplayName("입찰 이벤트의 종료 시각이 저장된 종료 예정 시각보다 이르면 바꾸지 않는다")
    void bidWithEarlierEndAt_keepsLaterEnd() {
        recommendKafkaListener.handle(bid(2L, BIDDER_ID, END_AT.minusMinutes(1), END_AT.plusMinutes(4)));

        recommendKafkaListener.handle(bid(1L, OTHER_BIDDER_ID, END_AT.minusMinutes(3), END_AT.plusMinutes(2)));

        assertThat(auction().getEndAt()).isEqualTo(END_AT.plusMinutes(4));
    }

    @Test
    @DisplayName("낙찰 뒤에 늦게 처리된 입찰은 종료 예정 시각만 늘리고, 끝난 상태와 실제 종료 시각은 그대로 둔다")
    void bidProcessedAfterWon_extendsEndOnly() {
        LocalDateTime extendedEndAt = END_AT.plusMinutes(4);
        LocalDateTime winAt = END_AT.plusMinutes(5);
        recommendKafkaListener.handle(won(OTHER_BIDDER_ID, winAt));

        recommendKafkaListener.handle(bid(1L, OTHER_BIDDER_ID, END_AT.minusMinutes(1), extendedEndAt));

        RecommendAuction auction = auction();
        assertThat(auction.getStatus()).isEqualTo(ENDED);
        assertThat(auction.getEndedAt()).isEqualTo(winAt);
        assertThat(auction.getEndAt()).isEqualTo(extendedEndAt);
    }

    @Test
    @DisplayName("입찰자의 회원 복사본이 없으면 USER_NOT_FOUND로 실패하고, 상호작용·로그·종료 예정 시각이 바뀌지 않는다")
    void bidWithoutMemberCopy_throwsUserNotFound() {
        assertThatThrownBy(() -> recommendKafkaListener.handle(bid(1L, UNKNOWN_ID, END_AT.minusMinutes(3), END_AT.plusMinutes(2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        assertThat(interactions()).isEmpty();
        assertThat(logs()).isEmpty();
        assertThat(auction().getEndAt()).isEqualTo(END_AT);
    }

    @Test
    @DisplayName("경매 복사본이 없는 입찰은 AUCTION_NOT_FOUND로 실패하고 아무것도 저장하지 않는다")
    void bidWithoutAuctionCopy_throwsAuctionNotFound() {
        AuctionBidSucceedEvent event = new AuctionBidSucceedEvent(1L, UNKNOWN_ID, PRODUCT_ID, BIDDER_ID, 21000L, 1, END_AT, BID_AT);

        assertThatThrownBy(() -> recommendKafkaListener.handle(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AUCTION_NOT_FOUND);

        assertThat(interactions()).isEmpty();
        assertThat(logs()).isEmpty();
    }

    @Test
    @DisplayName("상품 복사본이 없는 입찰은 PRODUCT_NOT_FOUND로 실패하고 아무것도 저장하지 않는다")
    void bidWithoutProductCopy_throwsProductNotFound() {
        AuctionBidSucceedEvent event = new AuctionBidSucceedEvent(1L, AUCTION_ID, UNKNOWN_ID, BIDDER_ID, 21000L, 1, END_AT, BID_AT);

        assertThatThrownBy(() -> recommendKafkaListener.handle(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PRODUCT_NOT_FOUND);

        assertThat(interactions()).isEmpty();
        assertThat(logs()).isEmpty();
    }

    @Test
    @DisplayName("즉시 낙찰처럼 입찰과 낙찰의 시각이 같아도, 입찰을 먼저 처리하면 BID와 WIN 상호작용·로그가 하나씩 남고 경매가 끝난다")
    void instantWin_bidFirst_savesBidAndWin() {
        recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT));

        recommendKafkaListener.handle(won(BIDDER_ID, BID_AT));

        assertThat(auction().getStatus()).isEqualTo(ENDED);
        assertThat(interactions()).containsExactly(
                interactionRow(BIDDER_ID, BID, BID_AT),
                interactionRow(BIDDER_ID, WIN, BID_AT));
        assertThat(logs()).containsExactly(
                logRow(BIDDER_ID, BID, BID_AT),
                logRow(BIDDER_ID, WIN, BID_AT));
    }

    @Test
    @DisplayName("즉시 낙찰에서 낙찰을 먼저 처리해도 BID와 WIN 상호작용·로그가 하나씩 남는다")
    void instantWin_wonFirst_savesWinAndBid() {
        recommendKafkaListener.handle(won(BIDDER_ID, BID_AT));

        recommendKafkaListener.handle(bid(1L, BIDDER_ID, BID_AT, END_AT));

        assertThat(auction().getStatus()).isEqualTo(ENDED);
        assertThat(interactions()).containsExactly(
                interactionRow(BIDDER_ID, WIN, BID_AT),
                interactionRow(BIDDER_ID, BID, BID_AT));
        assertThat(logs()).containsExactly(
                logRow(BIDDER_ID, WIN, BID_AT),
                logRow(BIDDER_ID, BID, BID_AT));
    }

    @Test
    @DisplayName("상호작용은 회원·경매·유형마다 한 행만 저장할 수 있다(DB 유니크 키)")
    void interactionUniqueKey_allowsOneRowPerMemberAuctionType() {
        interactionRepository.saveAndFlush(new Interaction(savedBidder(), auction(), savedProduct(), BID, BID_AT));

        assertThatThrownBy(() -> interactionRepository.saveAndFlush(
                new Interaction(savedBidder(), auction(), savedProduct(), BID, BID_AT.plusMinutes(10))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> interactionRepository.saveAndFlush(
                new Interaction(savedBidder(), auction(), savedProduct(), WIN, WIN_AT)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("로그는 회원·경매·유형·발생 시각마다 한 행만 저장할 수 있다(DB 유니크 키)")
    void logUniqueKey_allowsOneRowPerMemberAuctionTypeTime() {
        interactionLogRepository.saveAndFlush(new InteractionLog(savedBidder(), auction(), savedProduct(), BID, BID_AT));

        assertThatThrownBy(() -> interactionLogRepository.saveAndFlush(
                new InteractionLog(savedBidder(), auction(), savedProduct(), BID, BID_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> interactionLogRepository.saveAndFlush(
                new InteractionLog(savedBidder(), auction(), savedProduct(), BID, BID_AT.plusMinutes(10))))
                .doesNotThrowAnyException();
    }

    private RecommendAuction auction() {
        return recommendAuctionRepository.findById(AUCTION_ID).orElseThrow();
    }

    private RecommendMember savedBidder() {
        return recommendMemberRepository.findById(BIDDER_ID).orElseThrow();
    }

    private RecommendProduct savedProduct() {
        return recommendProductRepository.findById(PRODUCT_ID).orElseThrow();
    }

    private List<InteractionRow> interactions() {
        return jdbcTemplate.query(
                "select member_id, auction_id, product_id, type, active, last_occurred_at from interaction order by id",
                (rs, rowNum) -> new InteractionRow(
                        rs.getLong("member_id"), rs.getLong("auction_id"), rs.getLong("product_id"),
                        rs.getString("type"), rs.getBoolean("active"),
                        rs.getObject("last_occurred_at", LocalDateTime.class)));
    }

    private List<LogRow> logs() {
        return jdbcTemplate.query(
                "select member_id, auction_id, product_id, type, last_occurred_at from interaction_log order by id",
                (rs, rowNum) -> new LogRow(
                        rs.getLong("member_id"), rs.getLong("auction_id"), rs.getLong("product_id"),
                        rs.getString("type"), rs.getObject("last_occurred_at", LocalDateTime.class)));
    }

    private static InteractionRow interactionRow(long memberId, InteractionType type, LocalDateTime lastOccurredAt) {
        return new InteractionRow(memberId, AUCTION_ID, PRODUCT_ID, type.name(), true, lastOccurredAt);
    }

    private static LogRow logRow(long memberId, InteractionType type, LocalDateTime occurredAt) {
        return new LogRow(memberId, AUCTION_ID, PRODUCT_ID, type.name(), occurredAt);
    }

    private static RecommendMember memberCopy(long memberId) {
        return new RecommendMember(memberId, UUID.randomUUID(), "회원" + memberId);
    }

    private static ProductChangedEvent productCreated() {
        return new ProductChangedEvent(PRODUCT_ID, CREATED, 0L, SELLER_ID, "듀얼센스", "ACCESSORY", "SONY", null,
                10000L, 50000L, START_AT, null, START_AT.minusDays(1));
    }

    private static AuctionStartedEvent started() {
        return new AuctionStartedEvent(AUCTION_ID, PRODUCT_ID, 10000L, 50000L, START_AT, END_AT, "ENGLISH");
    }

    private static AuctionBidSucceedEvent bid(long bidId, long bidderId, LocalDateTime bidAt, LocalDateTime endAt) {
        return new AuctionBidSucceedEvent(bidId, AUCTION_ID, PRODUCT_ID, bidderId, 20000L + bidId * 1000, (int) bidId, endAt, bidAt);
    }

    private static AuctionWonEvent won(long winnerId, LocalDateTime winAt) {
        return new AuctionWonEvent(AUCTION_ID, PRODUCT_ID, winnerId, SELLER_ID, 30000L, 3000L, winAt);
    }

    record InteractionRow(long memberId, long auctionId, long productId, String type, boolean active, LocalDateTime lastOccurredAt) {
    }

    record LogRow(long memberId, long auctionId, long productId, String type, LocalDateTime occurredAt) {
    }
}
