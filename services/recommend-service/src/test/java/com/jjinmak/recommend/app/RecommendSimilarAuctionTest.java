package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
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
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductChangeType.DELETED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.EMBEDDING_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendSimilarAuctionTest extends RecommendIntegrationTestSupport {

    static final long BASE_PRODUCT_ID = 101L;
    static final long BASE_AUCTION_ID = 1001L;
    static final long UNKNOWN_AUCTION_ID = 9999L;
    static final long SELLER_ID = 9L;
    static final long START_PRICE = 10000L;
    static final String CATEGORY = "ACCESSORY";
    static final String OTHER_MODEL = "text-embedding-3-small";
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

    @BeforeEach
    void setUp() {
        clean();
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        readyAuction(BASE_PRODUCT_ID, BASE_AUCTION_ID, 0);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("기준 상품과 임베딩이 가까운 순서로 진행 중인 경매를 돌려주고, 기준 경매 자신은 빠진다")
    void similar_ordersByEmbeddingSimilarity() {
        readyAuction(103L, 1003L, 40);
        readyAuction(104L, 1004L, 80);
        readyAuction(102L, 1002L, 10);

        assertThat(auctionIds(recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID)))
                .containsExactly(1002L, 1003L, 1004L);
    }

    @Test
    @DisplayName("응답 항목에는 후보 경매의 id, 상품 id, 카테고리, 현재가, 종료 시각이 들어간다")
    void similar_mapsCandidateFields() {
        recommendKafkaListener.handle(productEvent(102L, CREATED, 0L, "GAME"));
        recommendKafkaListener.handle(started(1002L, 102L, now.plusDays(2)));
        embedding(102L, "READY", model, 10);

        assertThat(recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID))
                .containsExactly(new AuctionResponseDto(1002L, 102L, "GAME", START_PRICE, now.plusDays(2)));
    }

    @Test
    @DisplayName("READY가 아닌 임베딩, 설정과 다른 모델의 임베딩, 임베딩 행이 없는 상품의 경매는 기준과 더 가까워도 빠진다")
    void similar_excludesUnusableEmbeddings() {
        readyAuction(102L, 1002L, 10);
        readyAuction(105L, 1005L, 5);
        embedding(105L, "PENDING", model, 5);
        readyAuction(106L, 1006L, 5);
        embedding(106L, "READY", OTHER_MODEL, 5);
        readyAuction(107L, 1007L, 5);
        productEmbeddingRepository.deleteById(107L);

        assertThat(auctionIds(recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID)))
                .containsExactly(1002L);
    }

    @Test
    @DisplayName("끝난 경매, 마감 시각이 지난 경매, 삭제된 상품의 경매는 기준과 더 가까워도 빠진다")
    void similar_excludesClosedAuctionsAndDeletedProducts() {
        readyAuction(102L, 1002L, 10);
        readyAuction(108L, 1008L, 5);
        recommendKafkaListener.handle(new AuctionFailedEvent(1008L, 108L, "NO_BID", now));
        recommendKafkaListener.handle(productEvent(109L, CREATED, 0L, CATEGORY));
        recommendKafkaListener.handle(started(1009L, 109L, now.minusHours(1)));
        embedding(109L, "READY", model, 5);
        readyAuction(110L, 1010L, 5);
        recommendKafkaListener.handle(productEvent(110L, DELETED, 1L, CATEGORY));

        assertThat(auctionIds(recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID)))
                .containsExactly(1002L);
    }

    @Test
    @DisplayName("후보가 10개보다 많으면 가까운 순서로 10개만 돌려준다")
    void similar_returnsAtMostTen() {
        LongStream.rangeClosed(1, 12).forEach(i -> readyAuction(200L + i, 2000L + i, i));

        assertThat(auctionIds(recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID)))
                .containsExactlyElementsOf(LongStream.rangeClosed(2001L, 2010L).boxed().toList());
    }

    @Test
    @DisplayName("기준 상품의 임베딩이 READY가 아니면 빈 목록을 돌려준다")
    void basePending_returnsEmptyList() {
        readyAuction(102L, 1002L, 10);
        jdbcTemplate.update("update product_embedding set status = 'PENDING' where id = ?", BASE_PRODUCT_ID);

        assertThat(recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("기준 상품의 임베딩이 설정과 다른 모델로 만들어졌으면 빈 목록을 돌려준다")
    void baseOtherModel_returnsEmptyList() {
        readyAuction(102L, 1002L, 10);
        jdbcTemplate.update("update product_embedding set model = ? where id = ?", OTHER_MODEL, BASE_PRODUCT_ID);

        assertThat(recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("기준 경매가 추천 쪽에 없으면 AUCTION_NOT_FOUND로 실패한다")
    void unknownAuction_throwsAuctionNotFound() {
        assertThatThrownBy(() -> recommendFacade.readRecommendedAuctionList(UNKNOWN_AUCTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AUCTION_NOT_FOUND);
    }

    @Test
    @DisplayName("기준 상품의 임베딩 행이 없으면 EMBEDDING_NOT_FOUND로 실패한다")
    void baseWithoutEmbeddingRow_throwsEmbeddingNotFound() {
        productEmbeddingRepository.deleteById(BASE_PRODUCT_ID);

        assertThatThrownBy(() -> recommendFacade.readRecommendedAuctionList(BASE_AUCTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EMBEDDING_NOT_FOUND);
    }

    private void readyAuction(long productId, long auctionId, double degrees) {
        recommendKafkaListener.handle(productEvent(productId, CREATED, 0L, CATEGORY));
        recommendKafkaListener.handle(started(auctionId, productId, now.plusDays(1)));
        embedding(productId, "READY", model, degrees);
    }

    private void embedding(long productId, String status, String embeddingModel, double degrees) {
        jdbcTemplate.update(
                "update product_embedding set status = ?, model = ?, embedding = CAST(? AS vector) where id = ?",
                status, embeddingModel, vector(degrees), productId);
    }

    private AuctionStartedEvent started(long auctionId, long productId, LocalDateTime endAt) {
        return new AuctionStartedEvent(auctionId, productId, START_PRICE, 50000L, now.minusDays(1), endAt, "ENGLISH");
    }

    private void clean() {
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    private static ProductChangedEvent productEvent(long productId, ProductChangeType changeType, long version, String category) {
        return new ProductChangedEvent(
                productId, changeType, version, SELLER_ID, "상품" + productId, category, "SONY", null,
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

    private static List<Long> auctionIds(List<AuctionResponseDto> result) {
        return result.stream().map(AuctionResponseDto::auctionId).toList();
    }
}
