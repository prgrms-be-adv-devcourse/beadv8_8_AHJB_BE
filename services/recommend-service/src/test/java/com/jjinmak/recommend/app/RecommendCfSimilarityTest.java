package com.jjinmak.recommend.app;

import com.jjinmak.recommend.domain.ProductCfSimilarity;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.out.repository.InteractionRepository;
import com.jjinmak.recommend.out.repository.ProductCfSimilarityRepository;
import com.jjinmak.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

class RecommendCfSimilarityTest extends RecommendIntegrationTestSupport {

    static final long LOCK_KEY = "product_cf_similarity".hashCode();
    static final double WIN_WEIGHT = 1.0;
    static final double BID_WEIGHT = 0.6;
    static final double WISH_WEIGHT = 0.4;

    @Autowired
    RecommendFacade recommendFacade;

    @Autowired
    ProductCfSimilarityRepository productCfSimilarityRepository;

    @Autowired
    InteractionRepository interactionRepository;

    @Autowired
    RecommendAuctionRepository recommendAuctionRepository;

    @Autowired
    RecommendMemberRepository recommendMemberRepository;

    @Autowired
    RecommendProductRepository recommendProductRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        for (long memberId = 1; memberId <= 4; memberId++) {
            recommendMemberRepository.save(new RecommendMember(memberId, UUID.randomUUID(), "회원" + memberId));
        }
    }

    @AfterEach
    void tearDown() {
        productCfSimilarityRepository.deleteAllInBatch();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    @Test
    @DisplayName("진행 중 경매 상품과 함께 반응한 회원이 2명 이상인 상품의 쌍마다 코사인 유사도와 함께 반응한 회원 수를 저장한다")
    void refresh_savesCosineSimilarityAndCoCountPerPair() {
        product(101);
        product(205);
        product(307);
        activeAuction(1101, 101);
        endedAuction(1205, 205);
        endedAuction(1307, 307);
        interaction(1, 1101, 101, "BID");
        interaction(1, 1205, 205, "BID");
        interaction(1, 1205, 205, "WIN");
        interaction(2, 1101, 101, "BID");
        interaction(2, 1205, 205, "BID");
        interaction(3, 1101, 101, "WISH");
        interaction(3, 1307, 307, "BID");
        interaction(4, 1205, 205, "WISH", false);

        recommendFacade.refreshCfSimilarity();

        List<ProductCfSimilarity> similarities = similarities();
        assertThat(similarities)
                .extracting(ProductCfSimilarity::getProductId, ProductCfSimilarity::getOtherProductId, ProductCfSimilarity::getCoCount)
                .containsExactly(tuple(101L, 205L, 2));
        assertThat(similarities.get(0).getScore()).isCloseTo(
                cosine(new double[]{BID_WEIGHT, BID_WEIGHT, WISH_WEIGHT, 0}, new double[]{WIN_WEIGHT, BID_WEIGHT, 0, 0}),
                within(1e-9)
        );
        assertThat(similarities.get(0).getComputedAt()).isNotNull();
    }

    @Test
    @DisplayName("진행 중 상품 하나가 여러 상품과 각각 쌍을 이룰 수 있다")
    void refresh_savesEveryPairOfOneProduct() {
        product(151);
        product(152);
        product(153);
        activeAuction(11151, 151);
        endedAuction(11152, 152);
        endedAuction(11153, 153);
        interaction(1, 11151, 151, "BID");
        interaction(2, 11151, 151, "BID");
        interaction(1, 11152, 152, "BID");
        interaction(2, 11152, 152, "BID");
        interaction(1, 11153, 153, "WISH");
        interaction(2, 11153, 153, "WISH");

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities())
                .extracting(ProductCfSimilarity::getProductId, ProductCfSimilarity::getOtherProductId, ProductCfSimilarity::getCoCount)
                .containsExactly(tuple(151L, 152L, 2), tuple(151L, 153L, 2));
    }

    @Test
    @DisplayName("같은 회원이 한 상품에 남긴 여러 기록은 가장 큰 가중치 하나로 계산한다")
    void refresh_usesLargestWeightPerMemberAndProduct() {
        product(11);
        product(12);
        activeAuction(1011, 11);
        endedAuction(1012, 12);
        interaction(1, 1011, 11, "BID");
        interaction(2, 1011, 11, "BID");
        interaction(1, 1012, 12, "BID");
        interaction(1, 1012, 12, "WIN");
        interaction(2, 1012, 12, "WISH");

        recommendFacade.refreshCfSimilarity();

        List<ProductCfSimilarity> similarities = similarities();
        assertThat(similarities).hasSize(1);
        assertThat(similarities.get(0).getScore()).isCloseTo(
                cosine(new double[]{BID_WEIGHT, BID_WEIGHT}, new double[]{WIN_WEIGHT, WISH_WEIGHT}), within(1e-9)
        );
    }

    @Test
    @DisplayName("함께 반응한 회원이 1명뿐인 쌍은 저장하지 않는다")
    void refresh_dropsPairWithOneCoReactingMember() {
        product(21);
        product(22);
        activeAuction(1021, 21);
        endedAuction(1022, 22);
        interaction(1, 1021, 21, "BID");
        interaction(2, 1021, 21, "BID");
        interaction(1, 1022, 22, "BID");

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    @Test
    @DisplayName("취소한 찜은 함께 반응한 회원 수에서 빠진다")
    void refresh_leavesCanceledWishOutOfCoCount() {
        product(31);
        product(32);
        activeAuction(1031, 31);
        endedAuction(1032, 32);
        interaction(1, 1031, 31, "BID");
        interaction(2, 1031, 31, "BID");
        interaction(1, 1032, 32, "WISH");
        interaction(2, 1032, 32, "WISH", false);

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    @Test
    @DisplayName("취소한 찜은 상품의 크기를 계산할 때도 빠진다")
    void refresh_leavesCanceledWishOutOfNorm() {
        product(41);
        product(42);
        activeAuction(1041, 41);
        endedAuction(1042, 42);
        interaction(1, 1041, 41, "BID");
        interaction(2, 1041, 41, "BID");
        interaction(1, 1042, 42, "WISH");
        interaction(2, 1042, 42, "WISH");
        interaction(3, 1042, 42, "WISH", false);

        recommendFacade.refreshCfSimilarity();

        List<ProductCfSimilarity> similarities = similarities();
        assertThat(similarities).hasSize(1);
        assertThat(similarities.get(0).getScore()).isCloseTo(
                cosine(new double[]{BID_WEIGHT, BID_WEIGHT, 0}, new double[]{WISH_WEIGHT, WISH_WEIGHT, 0}), within(1e-9)
        );
    }

    @Test
    @DisplayName("두 상품이 모두 진행 중이면 양쪽 방향 행을 같은 점수로 저장한다")
    void refresh_savesBothDirectionsWhenBothAreInProgress() {
        product(51);
        product(52);
        activeAuction(1051, 51);
        activeAuction(1052, 52);
        interaction(1, 1051, 51, "BID");
        interaction(1, 1052, 52, "WISH");
        interaction(2, 1051, 51, "WISH");
        interaction(2, 1052, 52, "BID");

        recommendFacade.refreshCfSimilarity();

        List<ProductCfSimilarity> similarities = similarities();
        assertThat(similarities)
                .extracting(ProductCfSimilarity::getProductId, ProductCfSimilarity::getOtherProductId, ProductCfSimilarity::getCoCount)
                .containsExactly(tuple(51L, 52L, 2), tuple(52L, 51L, 2));
        assertThat(similarities.get(0).getScore()).isCloseTo(
                cosine(new double[]{BID_WEIGHT, WISH_WEIGHT}, new double[]{WISH_WEIGHT, BID_WEIGHT}), within(1e-9)
        );
        assertThat(similarities.get(1).getScore()).isEqualTo(similarities.get(0).getScore());
    }

    @Test
    @DisplayName("끝난 경매의 상품끼리는 쌍을 만들지 않는다")
    void refresh_skipsPairsOfEndedAuctionProducts() {
        product(61);
        product(62);
        endedAuction(1061, 61);
        endedAuction(1062, 62);
        interaction(1, 1061, 61, "WIN");
        interaction(2, 1061, 61, "BID");
        interaction(1, 1062, 62, "BID");
        interaction(2, 1062, 62, "WIN");

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    @Test
    @DisplayName("ACTIVE여도 마감 시각이 지난 경매의 상품은 계산 대상이 아니다")
    void refresh_skipsActiveAuctionPastEndAt() {
        product(71);
        product(72);
        auction(1071, 71, "ACTIVE", now.minusMinutes(1));
        endedAuction(1072, 72);
        interaction(1, 1071, 71, "BID");
        interaction(2, 1071, 71, "BID");
        interaction(1, 1072, 72, "BID");
        interaction(2, 1072, 72, "BID");

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    @Test
    @DisplayName("마감 시각 전에 끝난 경매(즉시 낙찰)의 상품은 계산 대상이 아니다")
    void refresh_skipsAuctionEndedBeforeEndAt() {
        product(81);
        product(82);
        auction(1081, 81, "ENDED", now.plusDays(1));
        endedAuction(1082, 82);
        interaction(1, 1081, 81, "WIN");
        interaction(2, 1081, 81, "BID");
        interaction(1, 1082, 82, "BID");
        interaction(2, 1082, 82, "BID");

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 상품의 경매는 계산 대상이 아니다")
    void refresh_skipsDeletedProduct() {
        product(91, true);
        product(92);
        activeAuction(1091, 91);
        endedAuction(1092, 92);
        interaction(1, 1091, 91, "BID");
        interaction(2, 1091, 91, "BID");
        interaction(1, 1092, 92, "BID");
        interaction(2, 1092, 92, "BID");

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    @Test
    @DisplayName("같은 상품의 여러 경매에 남긴 기록은 상품 하나로 묶어 계산한다")
    void refresh_groupsRecordsOfSameProductAcrossAuctions() {
        product(111);
        product(112);
        auction(11110, 111, "ENDED", now.minusDays(1));
        activeAuction(11111, 111);
        endedAuction(1112, 112);
        interaction(1, 11110, 111, "BID");
        interaction(1, 11111, 111, "BID");
        interaction(2, 11111, 111, "WISH");
        interaction(1, 1112, 112, "WIN");
        interaction(2, 1112, 112, "BID");

        recommendFacade.refreshCfSimilarity();

        List<ProductCfSimilarity> similarities = similarities();
        assertThat(similarities)
                .extracting(ProductCfSimilarity::getProductId, ProductCfSimilarity::getOtherProductId, ProductCfSimilarity::getCoCount)
                .containsExactly(tuple(111L, 112L, 2));
        assertThat(similarities.get(0).getScore()).isCloseTo(
                cosine(new double[]{BID_WEIGHT, WISH_WEIGHT}, new double[]{WIN_WEIGHT, BID_WEIGHT}), within(1e-9)
        );
    }

    @Test
    @DisplayName("다시 계산하면 이전 값을 지우고 새 값으로 바꾼다")
    void refreshAgain_replacesPreviousValues() {
        product(121);
        product(122);
        activeAuction(11121, 121);
        endedAuction(11122, 122);
        interaction(1, 11121, 121, "BID");
        interaction(2, 11121, 121, "BID");
        interaction(1, 11122, 122, "BID");
        interaction(2, 11122, 122, "BID");
        recommendFacade.refreshCfSimilarity();
        ProductCfSimilarity first = similarities().get(0);
        interaction(3, 11121, 121, "WISH");
        interaction(3, 11122, 122, "BID");

        recommendFacade.refreshCfSimilarity();

        List<ProductCfSimilarity> similarities = similarities();
        assertThat(similarities)
                .extracting(ProductCfSimilarity::getProductId, ProductCfSimilarity::getOtherProductId, ProductCfSimilarity::getCoCount)
                .containsExactly(tuple(121L, 122L, 3));
        assertThat(similarities.get(0).getScore()).isCloseTo(
                cosine(new double[]{BID_WEIGHT, BID_WEIGHT, WISH_WEIGHT}, new double[]{BID_WEIGHT, BID_WEIGHT, BID_WEIGHT}),
                within(1e-9)
        );
        assertThat(similarities.get(0).getComputedAt()).isAfter(first.getComputedAt());
    }

    @Test
    @DisplayName("다시 계산했을 때 기준에 못 미치게 된 쌍은 지워진다")
    void refreshAgain_deletesPairBelowMinCoCount() {
        product(131);
        product(132);
        activeAuction(11131, 131);
        endedAuction(11132, 132);
        interaction(1, 11131, 131, "BID");
        interaction(2, 11131, 131, "BID");
        interaction(1, 11132, 132, "WISH");
        interaction(2, 11132, 132, "WISH");
        recommendFacade.refreshCfSimilarity();
        assertThat(similarities()).hasSize(1);
        cancelWish(2, 132);

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    @Test
    @DisplayName("다른 트랜잭션이 같은 잠금을 쥐고 있으면 아무것도 바꾸지 않고, 잠금이 풀린 뒤에는 다시 계산한다")
    void refresh_skipsWhileAnotherTransactionHoldsLock() throws Exception {
        product(141);
        product(142);
        activeAuction(11141, 141);
        endedAuction(11142, 142);
        interaction(1, 11141, 141, "BID");
        interaction(2, 11141, 141, "BID");
        interaction(1, 11142, 142, "WISH");
        interaction(2, 11142, 142, "WISH");
        recommendFacade.refreshCfSimilarity();
        ProductCfSimilarity before = similarities().get(0);
        cancelWish(2, 142);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, LOCK_KEY);
                statement.execute();
            }

            recommendFacade.refreshCfSimilarity();

            List<ProductCfSimilarity> whileLocked = similarities();
            assertThat(whileLocked).hasSize(1);
            assertThat(whileLocked.get(0).getComputedAt()).isEqualTo(before.getComputedAt());
            connection.rollback();
        }

        recommendFacade.refreshCfSimilarity();

        assertThat(similarities()).isEmpty();
    }

    private void product(long productId) {
        product(productId, false);
    }

    private void product(long productId, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO recommend_product (id, created_at, updated_at, version, seller_id, product_name, category, manufacturer, thumbnail_url, deleted)
                VALUES (?, now(), now(), 0, 9, ?, 'ACCESSORY', 'SONY', null, ?)
                """, productId, "상품" + productId, deleted);
    }

    private void activeAuction(long auctionId, long productId) {
        auction(auctionId, productId, "ACTIVE", now.plusDays(1));
    }

    private void endedAuction(long auctionId, long productId) {
        auction(auctionId, productId, "ENDED", now.minusHours(1));
    }

    private void auction(long auctionId, long productId, String status, LocalDateTime endAt) {
        jdbcTemplate.update("""
                INSERT INTO recommend_auction (id, created_at, updated_at, product_id, instant_win_price, current_price, bid_count, start_at, end_at, ended_at, status)
                VALUES (?, now(), now(), ?, null, 10000, 0, ?, ?, ?, ?)
                """, auctionId, productId, now.minusDays(3), endAt, "ENDED".equals(status) ? now.minusHours(1) : null, status);
    }

    private void interaction(long memberId, long auctionId, long productId, String type) {
        interaction(memberId, auctionId, productId, type, true);
    }

    private void interaction(long memberId, long auctionId, long productId, String type, boolean active) {
        jdbcTemplate.update("""
                INSERT INTO interaction (created_at, updated_at, member_id, auction_id, product_id, type, active, last_occurred_at)
                VALUES (now(), now(), ?, ?, ?, ?, ?, ?)
                """, memberId, auctionId, productId, type, active, now.minusHours(2));
    }

    private void cancelWish(long memberId, long productId) {
        jdbcTemplate.update("UPDATE interaction SET active = false WHERE member_id = ? AND product_id = ? AND type = 'WISH'",
                memberId, productId);
    }

    private List<ProductCfSimilarity> similarities() {
        return productCfSimilarityRepository.findAll(Sort.by("productId", "otherProductId"));
    }

    private double cosine(double[] product, double[] otherProduct) {
        double dot = 0;
        double norm = 0;
        double otherNorm = 0;
        for (int i = 0; i < product.length; i++) {
            dot += product[i] * otherProduct[i];
            norm += product[i] * product[i];
            otherNorm += otherProduct[i] * otherProduct[i];
        }
        return dot / (Math.sqrt(norm) * Math.sqrt(otherNorm));
    }
}
