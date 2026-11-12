package com.jjinmak.recommend.app;

import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.in.RecommendScheduler;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.recommend.support.FakeEmbeddingModel;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductChangeType.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecommendEmbeddingGenerationTest extends RecommendIntegrationTestSupport {

    static final long DUALSENSE_ID = 10L;
    static final long SWITCH_ID = 11L;
    static final long STEAM_DECK_ID = 12L;

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    RecommendFacade recommendFacade;

    @Autowired
    FakeEmbeddingModel fakeEmbeddingModel;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    @Autowired
    RecommendProductRepository recommendProductRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.openai.embedding.model}")
    String model;

    RecommendScheduler recommendScheduler;

    @BeforeEach
    void setUp() {
        fakeEmbeddingModel.reset();
        recommendScheduler = new RecommendScheduler(recommendFacade);
    }

    @AfterEach
    void tearDown() {
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
        fakeEmbeddingModel.reset();
    }

    @Test
    @DisplayName("스케줄러가 PENDING 임베딩을 만들면 1536차원 벡터를 길이 1로 맞춰 저장하고, 설정의 모델 이름과 함께 READY로 바꾼다")
    void pending_becomesReadyWithNormalizedVector() {
        recommendKafkaListener.handle(created(DUALSENSE_ID, "듀얼센스"));

        recommendScheduler.requestEmbedding();

        assertThat(fakeEmbeddingModel.requestedTexts()).containsExactly(text("듀얼센스"));
        EmbeddingRow row = row(DUALSENSE_ID);
        assertThat(row.status()).isEqualTo("READY");
        assertThat(row.model()).isEqualTo(model);
        assertThat(row.dimensions()).isEqualTo(FakeEmbeddingModel.DIMENSIONS);
        assertThat(row.norm()).isCloseTo(1.0, within(1e-6));
        assertThat(row.first()).isCloseTo(normalizedFirst(text("듀얼센스")), within(1e-6f));
    }

    @Test
    @DisplayName("READY가 된 임베딩은 다음 실행에서 다시 만들지 않는다")
    void ready_isNotRequestedAgain() {
        recommendKafkaListener.handle(created(DUALSENSE_ID, "듀얼센스"));
        recommendScheduler.requestEmbedding();

        recommendScheduler.requestEmbedding();

        assertThat(fakeEmbeddingModel.requestedTexts()).hasSize(1);
    }

    @Test
    @DisplayName("상품명이 바뀌어 PENDING이 된 임베딩은 바뀐 텍스트로 다시 만들어 벡터를 바꾸고 READY가 된다")
    void renamed_isRegeneratedWithNewText() {
        recommendKafkaListener.handle(created(DUALSENSE_ID, "듀얼센스"));
        recommendScheduler.requestEmbedding();
        recommendKafkaListener.handle(event(DUALSENSE_ID, UPDATED, 1L, "듀얼센스 엣지"));

        recommendScheduler.requestEmbedding();

        assertThat(fakeEmbeddingModel.requestedTexts()).containsExactly(text("듀얼센스"), text("듀얼센스 엣지"));
        EmbeddingRow row = row(DUALSENSE_ID);
        assertThat(row.status()).isEqualTo("READY");
        assertThat(row.first()).isCloseTo(normalizedFirst(text("듀얼센스 엣지")), within(1e-6f));
    }

    @Test
    @DisplayName("저장된 모델이 설정의 모델과 다르면 READY여도 다시 만들고 설정의 모델로 바꾼다")
    void otherModel_isRegenerated() {
        recommendKafkaListener.handle(created(DUALSENSE_ID, "듀얼센스"));
        recommendScheduler.requestEmbedding();
        jdbcTemplate.update("update product_embedding set model = 'text-embedding-3-small' where id = ?", DUALSENSE_ID);

        recommendScheduler.requestEmbedding();

        assertThat(fakeEmbeddingModel.requestedTexts()).hasSize(2);
        EmbeddingRow row = row(DUALSENSE_ID);
        assertThat(row.status()).isEqualTo("READY");
        assertThat(row.model()).isEqualTo(model);
    }

    @Test
    @DisplayName("만들 대상은 PENDING 임베딩과 모델이 설정과 다른 임베딩이고, 설정의 모델로 READY인 임베딩은 빠진다")
    void findPendingEmbedding_selectsPendingAndOtherModel() {
        recommendKafkaListener.handle(created(DUALSENSE_ID, "듀얼센스"));
        recommendKafkaListener.handle(created(SWITCH_ID, "닌텐도 스위치"));
        recommendKafkaListener.handle(created(STEAM_DECK_ID, "스팀덱"));
        jdbcTemplate.update("update product_embedding set status = 'READY', model = ? where id = ?", model, SWITCH_ID);
        jdbcTemplate.update(
                "update product_embedding set status = 'READY', model = 'text-embedding-3-small' where id = ?", STEAM_DECK_ID);

        assertThat(recommendFacade.findPendingEmbedding()).containsExactlyInAnyOrder(DUALSENSE_ID, STEAM_DECK_ID);
    }

    @Test
    @DisplayName("한 상품의 임베딩 호출이 실패해도 다른 상품은 READY가 되고, 실패한 상품은 PENDING으로 남아 다음 실행에서 만든다")
    void oneFailure_doesNotStopOthers() {
        recommendKafkaListener.handle(created(DUALSENSE_ID, "듀얼센스"));
        recommendKafkaListener.handle(created(SWITCH_ID, "닌텐도 스위치"));
        fakeEmbeddingModel.failOn(text("듀얼센스"));

        recommendScheduler.requestEmbedding();

        assertThat(row(DUALSENSE_ID).status()).isEqualTo("PENDING");
        assertThat(row(DUALSENSE_ID).dimensions()).isNull();
        assertThat(row(SWITCH_ID).status()).isEqualTo("READY");

        fakeEmbeddingModel.reset();
        recommendScheduler.requestEmbedding();

        assertThat(row(DUALSENSE_ID).status()).isEqualTo("READY");
        assertThat(fakeEmbeddingModel.requestedTexts()).containsExactly(text("듀얼센스"));
    }

    private EmbeddingRow row(long productId) {
        return jdbcTemplate.queryForObject(
                "select status, model, vector_dims(embedding) as dimensions, vector_norm(embedding) as norm, "
                        + "(embedding::real[])[1] as first from product_embedding where id = ?",
                (rs, rowNum) -> new EmbeddingRow(
                        rs.getString("status"), rs.getString("model"), rs.getObject("dimensions", Integer.class),
                        rs.getObject("norm", Double.class), rs.getObject("first", Float.class)),
                productId);
    }

    private static String text(String productName) {
        return "상품명: %s / 카테고리: ACCESSORY / 제조사: SONY".formatted(productName);
    }

    private static float normalizedFirst(String text) {
        double length = text.length();
        return (float) (length / Math.sqrt(length * length + FakeEmbeddingModel.DIMENSIONS - 1));
    }

    private static ProductChangedEvent created(long productId, String productName) {
        return event(productId, CREATED, 0L, productName);
    }

    private static ProductChangedEvent event(long productId, ProductChangeType changeType, long version, String productName) {
        return new ProductChangedEvent(
                productId, changeType, version, 20L, productName, "ACCESSORY", "SONY", null,
                10000L, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)
        );
    }

    private record EmbeddingRow(String status, String model, Integer dimensions, Double norm, Float first) {
    }
}
