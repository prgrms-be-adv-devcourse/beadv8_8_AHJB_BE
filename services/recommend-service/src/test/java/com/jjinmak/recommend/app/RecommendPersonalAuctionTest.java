package com.jjinmak.recommend.app;

import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.repository.InteractionLogRepository;
import com.jjinmak.recommend.out.repository.InteractionRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductChangeType.DELETED;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendPersonalAuctionTest extends RecommendIntegrationTestSupport {

    static final long SELLER_ID = 9L;
    static final long START_PRICE = 10000L;
    static final String CATEGORY = "ACCESSORY";
    static final String OTHER_MODEL = "other-model";
    static final int DIMENSIONS = 1536;

    @Autowired
    RecommendFacade recommendFacade;

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
    ProductEmbeddingRepository productEmbeddingRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.openai.embedding.model}")
    String model;

    LocalDateTime now;

    long bidSequence;

    @BeforeEach
    void setUp() {
        clean();
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        bidSequence = 0;
        LongStream.rangeClosed(1, 3)
                .forEach(memberId -> recommendMemberRepository.save(new RecommendMember(memberId, UUID.randomUUID(), "회원" + memberId)));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("입찰한 상품과 임베딩이 가까운 순서로 진행 중인 경매를 돌려준다")
    void personal_ordersByTasteSimilarity() {
        readyAuction(101L, 1001L, 0, now.plusDays(1), SELLER_ID);
        bid(1001L, 101L, 1L, now.plusDays(1), now.minusHours(1));
        readyAuction(102L, 1002L, 80, now.plusDays(1), SELLER_ID);
        readyAuction(103L, 1003L, 10, now.plusDays(1), SELLER_ID);
        readyAuction(104L, 1004L, 40, now.plusDays(1), SELLER_ID);

        assertThat(personalAuctionIds(1L)).containsExactly(1003L, 1004L, 1002L);
    }

    @Test
    @DisplayName("본인이 입찰한 경매, 본인이 판매하는 경매, 끝난 경매, 마감 시각이 지난 경매, 삭제된 상품의 경매는 취향이 비슷해도 맞춤 결과와 채운 결과 모두에서 빠진다")
    void personal_excludesOwnBidOwnSellingAndClosedAuctions() {
        readyAuction(101L, 1001L, 0, now.plusDays(1), SELLER_ID);
        bid(1001L, 101L, 1L, now.plusDays(1), now.minusHours(1));
        readyAuction(102L, 1002L, 1, now.plusDays(1), 1L);
        readyAuction(103L, 1003L, 2, now.plusDays(1), SELLER_ID);
        recommendKafkaListener.handle(new AuctionFailedEvent(1003L, 103L, "NO_WINNER", now));
        readyAuction(104L, 1004L, 3, now.minusHours(1), SELLER_ID);
        readyAuction(105L, 1005L, 4, now.plusDays(1), SELLER_ID);
        recommendKafkaListener.handle(productEvent(105L, DELETED, 1L, SELLER_ID));
        readyAuction(106L, 1006L, 60, now.plusDays(1), SELLER_ID);

        assertThat(personalAuctionIds(1L)).containsExactly(1006L);
    }

    @Test
    @DisplayName("맞춤 결과가 10개보다 적으면 남은 자리를 인기 경매 순서로 채우고, 이미 들어간 경매는 다시 넣지 않는다")
    void personal_fillsRemainingSlotsWithPopularAuctions() {
        readyAuction(101L, 1001L, 0, now.plusDays(1), SELLER_ID);
        bid(1001L, 101L, 1L, now.plusDays(1), now.minusHours(1));
        readyAuction(102L, 1002L, 40, now.plusDays(1), SELLER_ID);
        readyAuction(103L, 1003L, 10, now.plusDays(1), SELLER_ID);
        pendingAuction(107L, 1007L, now.plusHours(2), SELLER_ID);
        pendingAuction(108L, 1008L, now.plusDays(3), SELLER_ID);
        bid(1008L, 108L, 2L, now.plusDays(3), now.minusHours(1));

        assertThat(personalAuctionIds(1L)).containsExactly(1003L, 1002L, 1008L, 1007L);
    }

    @Test
    @DisplayName("맞춤 결과와 채운 인기 경매를 합쳐 10개까지만 돌려준다")
    void personal_returnsAtMostTenAfterFilling() {
        readyAuction(100L, 1000L, 0, now.plusDays(5), SELLER_ID);
        bid(1000L, 100L, 1L, now.plusDays(5), now.minusHours(1));
        readyAuction(101L, 1001L, 10, now.plusDays(5), SELLER_ID);
        readyAuction(102L, 1002L, 20, now.plusDays(5), SELLER_ID);
        readyAuction(103L, 1003L, 30, now.plusDays(5), SELLER_ID);
        LongStream.rangeClosed(1, 10).forEach(i -> pendingAuction(110L + i, 1010L + i, now.plusHours(i), SELLER_ID));

        assertThat(personalAuctionIds(1L))
                .containsExactly(1001L, 1002L, 1003L, 1011L, 1012L, 1013L, 1014L, 1015L, 1016L, 1017L);
    }

    @Test
    @DisplayName("맞춤 결과가 10개면 인기 경매로 채우지 않고 맞춤 결과 10개만 돌려준다")
    void personal_returnsTenScoredAuctionsWithoutFilling() {
        readyAuction(100L, 1000L, 0, now.plusDays(1), SELLER_ID);
        bid(1000L, 100L, 1L, now.plusDays(1), now.minusHours(1));
        LongStream.rangeClosed(1, 12).forEach(i -> readyAuction(100L + i, 1000L + i, i, now.plusDays(1), SELLER_ID));
        pendingAuction(113L, 1013L, now.plusMinutes(30), SELLER_ID);

        assertThat(personalAuctionIds(1L))
                .containsExactlyElementsOf(LongStream.rangeClosed(1001L, 1010L).boxed().toList());
    }

    @Test
    @DisplayName("임베딩이 준비된 상품의 기록이 없으면 인기 경매로 응답하고, 본인이 입찰하거나 판매하는 경매는 여기서도 빠진다")
    void personal_withoutUsableRecords_returnsPopularWithExclusions() {
        pendingAuction(301L, 3001L, now.plusHours(3), SELLER_ID);
        bid(3001L, 301L, 3L, now.plusHours(3), now.minusHours(1));
        readyAuction(302L, 3002L, 0, now.plusHours(1), 3L);
        readyAuction(303L, 3003L, 0, now.plusHours(5), SELLER_ID);
        pendingAuction(304L, 3004L, now.plusHours(10), SELLER_ID);
        bid(3004L, 304L, 2L, now.plusHours(10), now.minusHours(1));

        assertThat(personalAuctionIds(3L)).containsExactly(3004L, 3003L);
    }

    @Test
    @DisplayName("찜한 상품은 취향 기록으로 쓰이고, 찜한 경매 자신은 결과에서 빠진다")
    void personal_activeWish_isRecordAndExclusion() {
        readyAuction(101L, 1001L, 0, now.plusDays(1), SELLER_ID);
        wish(1L, 1001L, 101L, true, now.minusHours(1));
        readyAuction(102L, 1002L, 60, now.plusDays(1), SELLER_ID);
        readyAuction(103L, 1003L, 10, now.plusDays(1), SELLER_ID);

        assertThat(personalAuctionIds(1L)).containsExactly(1003L, 1002L);
    }

    @Test
    @DisplayName("찜만 있는 회원은 인기 경매를 받고, 찜한 경매는 빠지지만 찜을 취소한 경매는 그대로 나온다")
    void personal_wishOnlyMember_popularExcludesOnlyActiveWish() {
        pendingAuction(301L, 3001L, now.plusHours(2), SELLER_ID);
        wish(3L, 3001L, 301L, true, now.minusHours(1));
        readyAuction(302L, 3002L, 0, now.plusHours(5), SELLER_ID);
        wish(3L, 3002L, 302L, false, now.minusHours(1));
        readyAuction(303L, 3003L, 30, now.plusDays(1), SELLER_ID);

        assertThat(personalAuctionIds(3L)).containsExactly(3002L, 3003L);
    }

    @Test
    @DisplayName("설정 모델이 아닌 임베딩을 가진 경매는 맞춤 점수를 받지 않고 인기 경매로만 채워진다")
    void personal_candidateWithOtherModel_isFilledOnlyAsPopular() {
        readyAuction(101L, 1001L, 0, now.plusDays(1), SELLER_ID);
        bid(1001L, 101L, 1L, now.plusDays(1), now.minusHours(1));
        readyAuction(102L, 1002L, 40, now.plusDays(1), SELLER_ID);
        readyAuction(103L, 1003L, 5, now.plusDays(1), SELLER_ID);
        embedding(103L, 5, OTHER_MODEL);

        assertThat(personalAuctionIds(1L)).containsExactly(1002L, 1003L);
    }

    @Test
    @DisplayName("쓸 수 있는 기록이 50개보다 많으면 최근 50개만 쓴다")
    void personal_usesLatestFiftyRecords() {
        LongStream.rangeClosed(1, 50).forEach(i -> {
            readyAuction(600L + i, 6000L + i, 0, now.plusDays(1), SELLER_ID);
            bid(6000L + i, 600L + i, 1L, now.plusDays(1), now.minusHours(1));
        });
        readyAuction(651L, 6051L, 90, now.plusDays(1), SELLER_ID);
        bid(6051L, 651L, 1L, now.plusDays(1), now.minusDays(2));
        readyAuction(660L, 6060L, 90, now.plusDays(1), SELLER_ID);
        readyAuction(661L, 6061L, 45, now.plusDays(1), SELLER_ID);

        assertThat(personalAuctionIds(1L)).containsExactly(6061L, 6060L);
    }

    @Test
    @DisplayName("최근 50개는 임베딩이 준비되고 설정 모델이며 취소되지 않은 기록 가운데서 고르고, 찜을 취소한 경매는 후보에 남는다")
    void personal_picksFiftyFromUsableRecords() {
        LongStream.rangeClosed(1, 49).forEach(i -> {
            readyAuction(700L + i, 7000L + i, 0, now.plusDays(1), SELLER_ID);
            bid(7000L + i, 700L + i, 1L, now.plusDays(1), now.minusHours(1));
        });
        readyAuction(750L, 7050L, 90, now.plusDays(1), SELLER_ID);
        bid(7050L, 750L, 1L, now.plusDays(1), now.minusDays(2));
        readyAuction(751L, 7051L, 0, now.plusDays(1), SELLER_ID);
        jdbcTemplate.update("update product_embedding set status = 'PENDING' where id = ?", 751L);
        bid(7051L, 751L, 1L, now.plusDays(1), now.minusMinutes(10));
        readyAuction(752L, 7052L, 0, now.plusDays(1), SELLER_ID);
        embedding(752L, 0, OTHER_MODEL);
        bid(7052L, 752L, 1L, now.plusDays(1), now.minusMinutes(10));
        readyAuction(753L, 7053L, 0, now.plusDays(1), SELLER_ID);
        wish(1L, 7053L, 753L, false, now.minusMinutes(10));
        readyAuction(760L, 7060L, 90, now.plusDays(1), SELLER_ID);
        readyAuction(761L, 7061L, 45, now.plusDays(1), SELLER_ID);

        assertThat(personalAuctionIds(1L)).containsExactly(7053L, 7060L, 7061L);
    }

    @Test
    @DisplayName("오래된 낙찰은 가중치가 줄어 최근 입찰보다 덜 반영된다")
    void personal_oldWinDecaysBelowRecentBid() {
        recommendKafkaListener.handle(productEvent(201L, CREATED, 0L, SELLER_ID));
        embedding(201L, 90, model);
        recommendKafkaListener.handle(new AuctionStartedEvent(2001L, 201L, START_PRICE, 50000L, now.minusDays(61), now.minusDays(60), "ENGLISH"));
        recommendKafkaListener.handle(new AuctionWonEvent(2001L, 201L, 2L, SELLER_ID, 30000L, 3000L, now.minusDays(60)));
        readyAuction(202L, 2002L, 180, now.plusDays(1), SELLER_ID);
        bid(2002L, 202L, 2L, now.plusDays(1), now.minusHours(1));
        readyAuction(203L, 2003L, 90, now.plusDays(1), SELLER_ID);
        readyAuction(204L, 2004L, 170, now.plusDays(1), SELLER_ID);

        assertThat(personalAuctionIds(2L)).containsExactly(2004L, 2003L);
    }

    @Test
    @DisplayName("취향이 다른 두 회원은 상위 5개 결과가 서로 다르다")
    void personal_differentTastes_giveDifferentTopFive() {
        readyAuction(400L, 4000L, 0, now.plusDays(1), SELLER_ID);
        readyAuction(418L, 4018L, 180, now.plusDays(1), SELLER_ID);
        bid(4000L, 400L, 1L, now.plusDays(1), now.minusHours(1));
        bid(4018L, 418L, 2L, now.plusDays(1), now.minusHours(1));
        LongStream.rangeClosed(1, 5).forEach(i -> {
            readyAuction(400L + i, 4000L + i, 10.0 * i, now.plusDays(1), SELLER_ID);
            readyAuction(412L + i, 4012L + i, 120.0 + 10.0 * i, now.plusDays(1), SELLER_ID);
        });

        assertThat(personalAuctionIds(1L).subList(0, 5)).containsExactly(4001L, 4002L, 4003L, 4004L, 4005L);
        assertThat(personalAuctionIds(2L).subList(0, 5)).containsExactly(4017L, 4016L, 4015L, 4014L, 4013L);
    }

    private List<Long> personalAuctionIds(long memberId) {
        return recommendFacade.readPersonalRecommendedAuctionList(memberId).stream()
                .map(AuctionResponseDto::auctionId)
                .toList();
    }

    private void readyAuction(long productId, long auctionId, double degrees, LocalDateTime endAt, long sellerId) {
        pendingAuction(productId, auctionId, endAt, sellerId);
        embedding(productId, degrees, model);
    }

    private void pendingAuction(long productId, long auctionId, LocalDateTime endAt, long sellerId) {
        recommendKafkaListener.handle(productEvent(productId, CREATED, 0L, sellerId));
        recommendKafkaListener.handle(new AuctionStartedEvent(auctionId, productId, START_PRICE, 50000L, now.minusDays(1), endAt, "ENGLISH"));
    }

    private void embedding(long productId, double degrees, String embeddingModel) {
        jdbcTemplate.update(
                "update product_embedding set status = 'READY', model = ?, embedding = CAST(? AS vector) where id = ?",
                embeddingModel, vector(degrees), productId);
    }

    private void bid(long auctionId, long productId, long bidderId, LocalDateTime endAt, LocalDateTime bidAt) {
        bidSequence++;
        recommendKafkaListener.handle(new AuctionBidSucceedEvent(
                bidSequence, auctionId, productId, bidderId, START_PRICE + bidSequence * 1000, (int) bidSequence,
                endAt, bidAt, null));
    }

    private void wish(long memberId, long auctionId, long productId, boolean active, LocalDateTime wishedAt) {
        jdbcTemplate.update(
                "insert into interaction (created_at, updated_at, member_id, auction_id, product_id, type, active, last_occurred_at) "
                        + "values (now(), now(), ?, ?, ?, 'WISH', ?, ?)",
                memberId, auctionId, productId, active, wishedAt);
    }

    private void clean() {
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    private static ProductChangedEvent productEvent(long productId, ProductChangeType changeType, long version, long sellerId) {
        return new ProductChangedEvent(
                productId, changeType, version, sellerId, "상품" + productId, CATEGORY, "SONY", null,
                START_PRICE, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)
        );
    }

    private static String vector(double degrees) {
        double radians = Math.toRadians(degrees);
        return Stream.concat(
                        Stream.of(Math.cos(radians), Math.sin(radians)),
                        Stream.generate(() -> 0.0).limit(DIMENSIONS - 2))
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
