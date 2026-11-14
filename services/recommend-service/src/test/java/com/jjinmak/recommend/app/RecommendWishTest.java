package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.contracts.product.event.ProductWishChangeType;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.repository.InteractionLogRepository;
import com.jjinmak.recommend.out.repository.InteractionRepository;
import com.jjinmak.recommend.out.repository.PendingInteractionRepository;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductWishChangeType.UNWISHED;
import static com.jjinmak.contracts.product.event.ProductWishChangeType.WISHED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendWishTest extends RecommendIntegrationTestSupport {

    static final long AUCTION_ID = 100L;
    static final long PRODUCT_ID = 55L;
    static final long OTHER_PRODUCT_ID = 56L;
    static final long SELLER_ID = 9L;
    static final long MEMBER_ID = 7L;
    static final long OTHER_MEMBER_ID = 8L;
    static final long UNKNOWN_ID = 999L;
    static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 10, 10, 0);
    static final LocalDateTime END_AT = START_AT.plusDays(3);
    static final LocalDateTime EARLY_WISH_AT = START_AT.minusHours(3);
    static final LocalDateTime EARLY_CANCEL_AT = START_AT.minusHours(2);
    static final LocalDateTime EARLY_REWISH_AT = START_AT.minusHours(1);
    static final LocalDateTime BID_AT = START_AT.plusMinutes(30);
    static final LocalDateTime WISH_AT = START_AT.plusHours(1);
    static final LocalDateTime CANCEL_AT = START_AT.plusHours(2);
    static final LocalDateTime REWISH_AT = START_AT.plusHours(3);

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
    PendingInteractionRepository pendingInteractionRepository;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createCopies() {
        recommendKafkaListener.handle(new ProductChangedEvent(PRODUCT_ID, CREATED, 0L, SELLER_ID, "듀얼센스", "ACCESSORY", "SONY", null,
                10000L, 50000L, START_AT, null, START_AT.minusDays(1)));
        recommendMemberRepository.save(new RecommendMember(MEMBER_ID, UUID.randomUUID(), "회원" + MEMBER_ID));
        recommendMemberRepository.save(new RecommendMember(OTHER_MEMBER_ID, UUID.randomUUID(), "회원" + OTHER_MEMBER_ID));
    }

    @AfterEach
    void tearDown() {
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        pendingInteractionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    @Test
    @DisplayName("찜 이벤트를 받으면 회원·경매의 WISH 상호작용을 active = true, 찜 시각으로 저장한다")
    void wished_savesActiveWish() {
        start();

        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, true, WISH_AT));
        assertThat(pendings()).isEmpty();
    }

    @Test
    @DisplayName("찜 취소 이벤트를 받으면 행을 지우지 않고 같은 행을 active = false, 취소 시각으로 바꾼다")
    void unwished_deactivatesSameRow() {
        start();
        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);

        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, false, CANCEL_AT));
    }

    @Test
    @DisplayName("찜 → 취소 → 다시 찜이면 active = true이고, 시각은 마지막 찜 시각이다")
    void rewished_activatesAgainAtLatestTime() {
        start();
        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);
        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        wish(MEMBER_ID, WISHED, AUCTION_ID, REWISH_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, true, REWISH_AT));
    }

    @Test
    @DisplayName("찜 행이 없을 때 취소 이벤트가 오면 active = false 행을 만든다")
    void unwishedWithoutWishRow_savesInactiveRow() {
        start();

        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, false, CANCEL_AT));
    }

    @Test
    @DisplayName("취소가 그보다 이른 찜보다 먼저 처리되어도, 최종 상태는 시각이 늦은 취소다")
    void cancelProcessedBeforeEarlierWish_keepsCancel() {
        start();
        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, false, CANCEL_AT));
    }

    @Test
    @DisplayName("찜하고 취소한 뒤에 같은 찜 이벤트가 늦게 다시 와도 취소 상태가 유지된다")
    void lateDuplicateWishAfterCancel_isIgnored() {
        start();
        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);
        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, false, CANCEL_AT));
    }

    @Test
    @DisplayName("다시 찜한 뒤에 예전 취소 이벤트가 늦게 다시 와도 찜 상태와 마지막 찜 시각이 유지된다")
    void lateOldCancelAfterRewish_isIgnored() {
        start();
        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);
        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);
        wish(MEMBER_ID, WISHED, AUCTION_ID, REWISH_AT);

        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, true, REWISH_AT));
    }

    @Test
    @DisplayName("같은 찜·취소 이벤트를 다시 받아도 실패하지 않고, 결과와 행 수가 그대로다")
    void redeliveredEvents_keepResult() {
        start();
        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);

        assertThatCode(() -> wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT)).doesNotThrowAnyException();
        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, true, WISH_AT));

        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        assertThatCode(() -> wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT)).doesNotThrowAnyException();
        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, false, CANCEL_AT));
    }

    @Test
    @DisplayName("찜은 회원마다 따로 저장되어, 한 회원의 취소가 다른 회원의 찜을 바꾸지 않는다")
    void wishesOfMembers_areSeparate() {
        start();
        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);
        wish(OTHER_MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);

        wish(OTHER_MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        assertThat(wishes()).containsExactly(
                wishRow(MEMBER_ID, true, WISH_AT),
                wishRow(OTHER_MEMBER_ID, false, CANCEL_AT));
    }

    @Test
    @DisplayName("입찰한 경매에 찜하고 취소해도 BID 행은 그대로 두고, WISH 행을 따로 만든다")
    void wishOnBidAuction_keepsBidRow() {
        start();
        recommendKafkaListener.handle(new AuctionBidSucceedEvent(1L, AUCTION_ID, PRODUCT_ID, MEMBER_ID, 21000L, 1, END_AT, BID_AT, null));

        wish(MEMBER_ID, WISHED, AUCTION_ID, WISH_AT);
        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        assertThat(interactionsOf(MEMBER_ID)).containsExactly(
                new TypedRow("BID", true, BID_AT),
                new TypedRow("WISH", false, CANCEL_AT));
    }

    @Test
    @DisplayName("경매 복사본이 없는 경매의 찜 이벤트는 AUCTION_NOT_FOUND로 실패하고 아무것도 저장하지 않는다")
    void wishWithoutAuctionCopy_throwsAuctionNotFound() {
        assertThatThrownBy(() -> wish(MEMBER_ID, WISHED, UNKNOWN_ID, WISH_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AUCTION_NOT_FOUND);

        assertThat(wishes()).isEmpty();
        assertThat(pendings()).isEmpty();
    }

    @Test
    @DisplayName("상품 복사본이 없는 상품의 찜 이벤트는 PRODUCT_NOT_FOUND로 실패하고 아무것도 저장하지 않는다")
    void wishWithoutProductCopy_throwsProductNotFound() {
        start();

        assertThatThrownBy(() -> wishOn(UNKNOWN_ID, MEMBER_ID, WISHED, AUCTION_ID, WISH_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PRODUCT_NOT_FOUND);

        assertThat(wishes()).isEmpty();
        assertThat(pendings()).isEmpty();
    }

    @Test
    @DisplayName("경매 시작 전 찜(auctionId 없음)은 대기 행으로만 저장하고, 상호작용은 만들지 않는다")
    void wishBeforeStart_savesPendingOnly() {
        wish(MEMBER_ID, WISHED, null, EARLY_WISH_AT);

        assertThat(pendings()).containsExactly(new PendingRow(MEMBER_ID, PRODUCT_ID, "WISHED", EARLY_WISH_AT));
        assertThat(wishes()).isEmpty();
    }

    @Test
    @DisplayName("경매가 시작되면 그 상품의 대기 찜을 회원마다 반영하고, 반영한 대기 행을 지운다")
    void started_appliesPendingWishesAndDeletesThem() {
        wish(MEMBER_ID, WISHED, null, EARLY_WISH_AT);
        wish(OTHER_MEMBER_ID, WISHED, null, EARLY_WISH_AT.plusMinutes(10));

        start();

        assertThat(recommendAuctionRepository.existsById(AUCTION_ID)).isTrue();
        assertThat(wishes()).containsExactly(
                wishRow(MEMBER_ID, true, EARLY_WISH_AT),
                wishRow(OTHER_MEMBER_ID, true, EARLY_WISH_AT.plusMinutes(10)));
        assertThat(pendings()).isEmpty();
    }

    @Test
    @DisplayName("시작 전에 찜과 취소가 여러 번 쌓여 있으면, 시작할 때 회원마다 마지막 이벤트의 상태와 시각으로 반영한다")
    void started_appliesLatestPendingStatePerMember() {
        wish(MEMBER_ID, WISHED, null, EARLY_WISH_AT);
        wish(MEMBER_ID, UNWISHED, null, EARLY_CANCEL_AT);
        wish(MEMBER_ID, WISHED, null, EARLY_REWISH_AT);
        wish(OTHER_MEMBER_ID, WISHED, null, EARLY_WISH_AT);
        wish(OTHER_MEMBER_ID, UNWISHED, null, EARLY_CANCEL_AT);

        start();

        assertThat(wishes()).containsExactly(
                wishRow(MEMBER_ID, true, EARLY_REWISH_AT),
                wishRow(OTHER_MEMBER_ID, false, EARLY_CANCEL_AT));
        assertThat(pendings()).isEmpty();
    }

    @Test
    @DisplayName("대기 행이 시각과 다른 순서로 쌓여도, 시작할 때 시각이 가장 늦은 이벤트가 최종 상태가 된다")
    void started_appliesPendingRegardlessOfArrivalOrder() {
        wish(MEMBER_ID, UNWISHED, null, EARLY_CANCEL_AT);
        wish(MEMBER_ID, WISHED, null, EARLY_WISH_AT);

        start();

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, false, EARLY_CANCEL_AT));
        assertThat(pendings()).isEmpty();
    }

    @Test
    @DisplayName("경매가 시작되어도 다른 상품의 대기 찜은 반영하지 않고 남겨 둔다")
    void started_keepsPendingOfOtherProducts() {
        wish(MEMBER_ID, WISHED, null, EARLY_WISH_AT);
        wishOn(OTHER_PRODUCT_ID, MEMBER_ID, WISHED, null, EARLY_WISH_AT);

        start();

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, true, EARLY_WISH_AT));
        assertThat(pendings()).containsExactly(new PendingRow(MEMBER_ID, OTHER_PRODUCT_ID, "WISHED", EARLY_WISH_AT));
    }

    @Test
    @DisplayName("시작 전 찜이 반영된 뒤 시작 후에 온 취소는 같은 행에 반영된다")
    void cancelAfterStart_updatesAppliedPendingWish() {
        wish(MEMBER_ID, WISHED, null, EARLY_WISH_AT);
        start();

        wish(MEMBER_ID, UNWISHED, AUCTION_ID, CANCEL_AT);

        assertThat(wishes()).containsExactly(wishRow(MEMBER_ID, false, CANCEL_AT));
        assertThat(pendings()).isEmpty();
    }

    private void start() {
        recommendKafkaListener.handle(new AuctionStartedEvent(AUCTION_ID, PRODUCT_ID, 10000L, 50000L, START_AT, END_AT, "ENGLISH"));
    }

    private void wish(long memberId, ProductWishChangeType changeType, Long auctionId, LocalDateTime occurredAt) {
        wishOn(PRODUCT_ID, memberId, changeType, auctionId, occurredAt);
    }

    private void wishOn(long productId, long memberId, ProductWishChangeType changeType, Long auctionId, LocalDateTime occurredAt) {
        recommendKafkaListener.handle(new ProductWishChangedEvent(productId, memberId, changeType, auctionId, occurredAt));
    }

    private List<WishRow> wishes() {
        return jdbcTemplate.query(
                "select member_id, auction_id, product_id, active, last_occurred_at from interaction where type = 'WISH' order by member_id",
                (rs, rowNum) -> new WishRow(
                        rs.getLong("member_id"), rs.getLong("auction_id"), rs.getLong("product_id"),
                        rs.getBoolean("active"), rs.getObject("last_occurred_at", LocalDateTime.class)));
    }

    private List<TypedRow> interactionsOf(long memberId) {
        return jdbcTemplate.query(
                "select type, active, last_occurred_at from interaction where member_id = ? order by type",
                (rs, rowNum) -> new TypedRow(
                        rs.getString("type"), rs.getBoolean("active"), rs.getObject("last_occurred_at", LocalDateTime.class)),
                memberId);
    }

    private List<PendingRow> pendings() {
        return jdbcTemplate.query(
                "select member_id, product_id, change_type, occurred_at from pending_interaction order by id",
                (rs, rowNum) -> new PendingRow(
                        rs.getLong("member_id"), rs.getLong("product_id"), rs.getString("change_type"),
                        rs.getObject("occurred_at", LocalDateTime.class)));
    }

    private static WishRow wishRow(long memberId, boolean active, LocalDateTime lastOccurredAt) {
        return new WishRow(memberId, AUCTION_ID, PRODUCT_ID, active, lastOccurredAt);
    }

    record WishRow(long memberId, long auctionId, long productId, boolean active, LocalDateTime lastOccurredAt) {
    }

    record TypedRow(String type, boolean active, LocalDateTime lastOccurredAt) {
    }

    record PendingRow(long memberId, long productId, String changeType, LocalDateTime occurredAt) {
    }
}
