package com.jjinmak.recommend.app;

import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.repository.InteractionLogRepository;
import com.jjinmak.recommend.out.repository.InteractionRepository;
import com.jjinmak.recommend.out.repository.ProductCfSimilarityRepository;
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
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendCfScoreTest extends RecommendIntegrationTestSupport {

    static final long MEMBER_ID = 1L;
    static final long SELLER_ID = 99L;
    static final long START_PRICE = 10000L;
    static final int DIMENSIONS = 1536;
    static final long BASE_PRODUCT_ID = 101L;
    static final long BASE_AUCTION_ID = 1001L;
    static final long CANDIDATE_A_PRODUCT_ID = 102L;
    static final long CANDIDATE_A_AUCTION_ID = 1002L;
    static final long CANDIDATE_B_PRODUCT_ID = 103L;
    static final long CANDIDATE_B_AUCTION_ID = 1003L;
    static final long OTHER_RECORD_PRODUCT_ID = 104L;
    static final long OTHER_RECORD_AUCTION_ID = 1004L;

    @Autowired
    RecommendFacade recommendFacade;

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    ProductCfSimilarityRepository productCfSimilarityRepository;

    @Autowired
    InteractionLogRepository interactionLogRepository;

    @Autowired
    InteractionRepository interactionRepository;

    @Autowired
    RecommendAuctionRepository recommendAuctionRepository;

    @Autowired
    RecommendMemberRepository recommendMemberRepository;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    @Autowired
    RecommendProductRepository recommendProductRepository;

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
        LongStream.rangeClosed(1, 10)
                .forEach(memberId -> recommendMemberRepository.save(new RecommendMember(memberId, UUID.randomUUID(), "회원" + memberId)));
        readyAuction(BASE_PRODUCT_ID, BASE_AUCTION_ID, 1.0);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("맞춤: 반응한 회원이 5명이어도 회원 기록과 협업 쌍이 없는 후보는 1단계 점수 그대로라 취향이 높은 순서를 지킨다")
    void personal_candidateWithoutPair_keepsPhaseOneScore() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.94);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.77);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_A_AUCTION_ID, CANDIDATE_B_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 신뢰도 1에 협업 유사도 1.0인 쌍이 있으면 취향이 조금 낮은 후보가 위로 올라간다")
    void personal_strongPairWithFullConfidence_outranksHigherTaste() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.6);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_A_AUCTION_ID, CANDIDATE_B_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 협업 점수는 후보 중 최댓값으로 나누지 않아, 약한 쌍(0.2) 하나로는 취향이 높은 후보를 넘지 못한다")
    void personal_collaborativeScore_isNotScaledToCandidateMaximum() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.3);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 0.2);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.6);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 반응한 회원이 2명이면 신뢰도 0.4가 반영된다")
    void personal_twoReactors_giveConfidenceOfPointFour() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.53);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_A_AUCTION_ID, CANDIDATE_B_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 반응한 회원이 5명보다 많아도 신뢰도는 1에서 멈춘다")
    void personal_moreThanFiveReactors_capConfidenceAtOne() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 2, 3, 4, 5, 6, 7, 8, 9);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.66);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 취소한 찜은 반응한 회원 수에 들어가지 않는다")
    void personal_canceledWish_isNotCountedAsReactor() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, false, 6);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.567);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 같은 회원의 입찰과 찜은 반응한 회원 한 명으로 센다")
    void personal_bidAndWishOfSameMember_countAsOneReactor() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        bid(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, 4);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.567);
        bid(CANDIDATE_B_AUCTION_ID, CANDIDATE_B_PRODUCT_ID, 6);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: (회원 기록 상품, 후보) 방향의 행만 있으면 협업 쌍이 없는 것으로 본다")
    void personal_reverseDirectionRowOnly_isIgnored() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(BASE_PRODUCT_ID, CANDIDATE_A_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.6);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 협업 점수에는 회원 기록의 가중치를 곱한다")
    void personal_collaborativeScore_isWeightedByRecord() {
        wish(BASE_AUCTION_ID, BASE_PRODUCT_ID, true, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.65);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("맞춤: 회원 기록 여러 개와 쌍이 있으면 '가중치 × 협업 유사도'의 합이 아니라 최댓값을 쓴다")
    void personal_pairsWithSeveralRecords_useMaximumNotSum() {
        bid(BASE_AUCTION_ID, BASE_PRODUCT_ID, MEMBER_ID);
        readyAuction(OTHER_RECORD_PRODUCT_ID, OTHER_RECORD_AUCTION_ID, 0, 0, 1);
        bid(OTHER_RECORD_AUCTION_ID, OTHER_RECORD_PRODUCT_ID, MEMBER_ID);
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 0.5);
        pair(CANDIDATE_A_PRODUCT_ID, OTHER_RECORD_PRODUCT_ID, 0.5);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.55);

        assertThat(personalAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: 반응한 회원이 5명이어도 기준 상품과 협업 쌍이 없으면 임베딩 유사도 순서 그대로다")
    void similar_candidateWithoutPair_keepsEmbeddingOrder() {
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.8);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.6);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_A_AUCTION_ID, CANDIDATE_B_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: 신뢰도 1에 기준 상품과 협업 유사도 1.0인 후보는 임베딩 유사도가 조금 낮아도 위로 올라간다")
    void similar_strongPairWithFullConfidence_outranksHigherSimilarity() {
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.75);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.81);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_A_AUCTION_ID, CANDIDATE_B_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: 반응한 회원이 2명이면 신뢰도 0.4가 반영된다")
    void similar_twoReactors_giveConfidenceOfPointFour() {
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.75);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.76);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_A_AUCTION_ID, CANDIDATE_B_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: 반응한 회원이 5명보다 많아도 신뢰도는 1에서 멈춘다")
    void similar_moreThanFiveReactors_capConfidenceAtOne() {
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.75);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 2, 3, 4, 5, 6, 7, 8, 9);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.85);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: 취소한 찜은 반응한 회원 수에 들어가지 않는다")
    void similar_canceledWish_isNotCountedAsReactor() {
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.75);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, false, 6);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.79);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: 같은 회원의 입찰과 찜은 반응한 회원 한 명으로 센다")
    void similar_bidAndWishOfSameMember_countAsOneReactor() {
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.75);
        bid(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, 4);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.79);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: (기준 상품, 후보) 방향의 행만 있으면 협업 쌍이 없는 것으로 본다")
    void similar_reverseDirectionRowOnly_isIgnored() {
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.75);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(BASE_PRODUCT_ID, CANDIDATE_A_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.8);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_B_AUCTION_ID, CANDIDATE_A_AUCTION_ID);
    }

    @Test
    @DisplayName("비슷한 경매: 기준 경매가 끝났어도 기준 상품과의 협업 유사도를 반영한다")
    void similar_endedBaseAuction_stillUsesPair() {
        recommendKafkaListener.handle(new AuctionFailedEvent(BASE_AUCTION_ID, BASE_PRODUCT_ID, "NO_BID", now));
        readyAuction(CANDIDATE_A_PRODUCT_ID, CANDIDATE_A_AUCTION_ID, 0.75);
        wish(CANDIDATE_A_AUCTION_ID, CANDIDATE_A_PRODUCT_ID, true, 4, 5, 6, 7, 8);
        pair(CANDIDATE_A_PRODUCT_ID, BASE_PRODUCT_ID, 1.0);
        readyAuction(CANDIDATE_B_PRODUCT_ID, CANDIDATE_B_AUCTION_ID, 0.81);

        assertThat(similarAuctionIds()).containsExactly(CANDIDATE_A_AUCTION_ID, CANDIDATE_B_AUCTION_ID);
    }

    @Test
    @DisplayName("반응한 회원 수를 상품별로 세는 interaction(product_id) 인덱스가 있다")
    void interaction_hasProductIdIndex() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexdef from pg_indexes where tablename = 'interaction' and indexname = 'idx_interaction_product_id'",
                String.class);

        assertThat(indexes).singleElement().asString().endsWith("(product_id)");
    }

    private List<Long> personalAuctionIds() {
        return recommendFacade.readPersonalRecommendedAuctionList(MEMBER_ID).stream()
                .map(AuctionResponseDto::auctionId)
                .toList();
    }

    private List<Long> similarAuctionIds() {
        return recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID).stream()
                .map(AuctionResponseDto::auctionId)
                .toList();
    }

    private void readyAuction(long productId, long auctionId, double cosine) {
        readyAuction(productId, auctionId, cosine, Math.sqrt(1 - cosine * cosine), 0);
    }

    private void readyAuction(long productId, long auctionId, double x, double y, double z) {
        recommendKafkaListener.handle(new ProductChangedEvent(
                productId, CREATED, 0L, SELLER_ID, "상품" + productId, "ACCESSORY", "SONY", null,
                START_PRICE, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)));
        recommendKafkaListener.handle(new AuctionStartedEvent(
                auctionId, productId, START_PRICE, 50000L, now.minusDays(1), now.plusDays(2), "ENGLISH"));
        jdbcTemplate.update(
                "update product_embedding set status = 'READY', model = ?, embedding = CAST(? AS vector) where id = ?",
                model, vector(x, y, z), productId);
    }

    private void bid(long auctionId, long productId, long bidderId) {
        bidSequence++;
        recommendKafkaListener.handle(new AuctionBidSucceedEvent(
                bidSequence, auctionId, productId, bidderId, START_PRICE + bidSequence * 1000, (int) bidSequence,
                now.plusDays(2), now.minusHours(1), null));
    }

    private void wish(long auctionId, long productId, boolean active, long... memberIds) {
        for (long memberId : memberIds) {
            jdbcTemplate.update(
                    "insert into interaction (created_at, updated_at, member_id, auction_id, product_id, type, active, last_occurred_at) "
                            + "values (now(), now(), ?, ?, ?, 'WISH', ?, ?)",
                    memberId, auctionId, productId, active, now.minusHours(1));
        }
    }

    private void pair(long productId, long otherProductId, double score) {
        jdbcTemplate.update(
                "insert into product_cf_similarity (product_id, other_product_id, score, co_count, computed_at) values (?, ?, ?, 2, now())",
                productId, otherProductId, score);
    }

    private void clean() {
        productCfSimilarityRepository.deleteAllInBatch();
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    private static String vector(double x, double y, double z) {
        return DoubleStream.concat(DoubleStream.of(x, y, z), DoubleStream.generate(() -> 0.0).limit(DIMENSIONS - 3))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
