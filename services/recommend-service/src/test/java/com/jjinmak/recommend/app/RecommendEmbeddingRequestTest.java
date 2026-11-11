package com.jjinmak.recommend.app;

import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.domain.ProductEmbedding;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.RecommendProductRepository;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductChangeType.UPDATED;
import static com.jjinmak.recommend.domain.EmbeddingStatus.PENDING;
import static com.jjinmak.recommend.domain.EmbeddingStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendEmbeddingRequestTest extends RecommendIntegrationTestSupport {

    static final long PRODUCT_ID = 10L;
    static final String DUALSENSE_HASH = "534d4d6b54c2ed35095651ef28075ca096f063fc12a49a8a3efa0a4f2d58dfb3";
    static final String DUALSENSE_EDGE_HASH = "cccac499f11fcb33db5c6769c2952db08f7a9f7903310e7fab22990cd7c56788";

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    @Autowired
    RecommendProductRepository recommendProductRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 등록 이벤트를 받으면 상품 id로 PENDING 임베딩을 만들고, 입력 텍스트의 SHA-256을 16진수 64자로 저장한다")
    void created_requestsEmbedding() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));

        ProductEmbedding embedding = embedding();
        assertThat(embedding.getStatus()).isEqualTo(PENDING);
        assertThat(embedding.getTextHash()).isEqualTo(DUALSENSE_HASH);
        assertThat(embedding.getModel()).isNull();
        assertThat(embedding.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("상품명이 바뀌면 READY였던 임베딩을 바뀐 텍스트의 해시와 함께 PENDING으로 되돌린다")
    void renamed_resetsToPendingWithNewHash() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));
        markReady();

        recommendKafkaListener.handle(event(UPDATED, 1L, "듀얼센스 엣지", null));

        ProductEmbedding embedding = embedding();
        assertThat(embedding.getStatus()).isEqualTo(PENDING);
        assertThat(embedding.getTextHash()).isEqualTo(DUALSENSE_EDGE_HASH);
    }

    @Test
    @DisplayName("썸네일만 바뀌면 입력 텍스트가 같으므로 READY 임베딩을 그대로 둔다")
    void thumbnailOnly_keepsReady() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", "https://img.test/1.jpg"));
        markReady();

        recommendKafkaListener.handle(event(UPDATED, 1L, "듀얼센스", "https://img.test/2.jpg"));

        ProductEmbedding embedding = embedding();
        assertThat(embedding.getStatus()).isEqualTo(READY);
        assertThat(embedding.getTextHash()).isEqualTo(DUALSENSE_HASH);
    }

    @Test
    @DisplayName("같은 등록 이벤트를 다시 받아도 임베딩은 하나이고 READY 상태가 그대로다")
    void redeliveredCreated_keepsReadyEmbedding() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));
        markReady();

        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));

        assertThat(productEmbeddingRepository.count()).isEqualTo(1);
        assertThat(embedding().getStatus()).isEqualTo(READY);
    }

    @Test
    @DisplayName("저장된 것보다 작은 version의 수정 이벤트는 텍스트가 달라도 임베딩을 바꾸지 않는다")
    void olderUpdate_keepsEmbedding() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));
        recommendKafkaListener.handle(event(UPDATED, 2L, "듀얼센스 엣지", null));
        markReady();

        recommendKafkaListener.handle(event(UPDATED, 1L, "듀얼센스", null));

        ProductEmbedding embedding = embedding();
        assertThat(embedding.getStatus()).isEqualTo(READY);
        assertThat(embedding.getTextHash()).isEqualTo(DUALSENSE_EDGE_HASH);
    }

    private void markReady() {
        jdbcTemplate.update(
                "update product_embedding set status = 'READY', model = 'text-embedding-3-large' where id = ?", PRODUCT_ID);
    }

    private ProductEmbedding embedding() {
        return productEmbeddingRepository.findById(PRODUCT_ID).orElseThrow();
    }

    private static ProductChangedEvent event(ProductChangeType changeType, long version, String productName, String thumbnailUrl) {
        return new ProductChangedEvent(
                PRODUCT_ID, changeType, version, 20L, productName, "ACCESSORY", "SONY", null,
                10000L, null, LocalDateTime.of(2026, 11, 12, 10, 0), thumbnailUrl, LocalDateTime.of(2026, 11, 10, 9, 0)
        );
    }
}
