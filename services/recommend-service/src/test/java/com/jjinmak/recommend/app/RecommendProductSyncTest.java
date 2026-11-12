package com.jjinmak.recommend.app;

import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.domain.RecommendProduct;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductChangeType.DELETED;
import static com.jjinmak.contracts.product.event.ProductChangeType.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RecommendProductSyncTest extends RecommendIntegrationTestSupport {

    static final long PRODUCT_ID = 10L;

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    RecommendProductRepository recommendProductRepository;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    @AfterEach
    void tearDown() {
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 등록 이벤트를 받으면 상품 id를 PK로 하는 복사본에 이벤트의 상품 정보를 저장한다")
    void created_savesCopy() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", "https://img.test/1.jpg"));

        RecommendProduct product = product();
        assertThat(product.getId()).isEqualTo(PRODUCT_ID);
        assertThat(product.getVersion()).isZero();
        assertThat(product.getSellerId()).isEqualTo(20L);
        assertThat(product.getProductName()).isEqualTo("듀얼센스");
        assertThat(product.getCategory()).isEqualTo("ACCESSORY");
        assertThat(product.getManufacturer()).isEqualTo("SONY");
        assertThat(product.getThumbnailUrl()).isEqualTo("https://img.test/1.jpg");
        assertThat(product.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("같은 등록 이벤트를 다시 받아도 실패하지 않고 복사본은 하나로 남는다")
    void redeliveredCreated_keepsOneCopy() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));

        assertThatCode(() -> recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null)))
                .doesNotThrowAnyException();
        assertThat(recommendProductRepository.count()).isEqualTo(1);
        assertThat(product().getProductName()).isEqualTo("듀얼센스");
    }

    @Test
    @DisplayName("더 큰 version의 수정 이벤트는 복사본을 덮어쓴다")
    void newerUpdate_overwritesCopy() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));

        recommendKafkaListener.handle(event(UPDATED, 2L, "듀얼센스 화이트", "https://img.test/2.jpg"));

        RecommendProduct product = product();
        assertThat(product.getVersion()).isEqualTo(2L);
        assertThat(product.getProductName()).isEqualTo("듀얼센스 화이트");
        assertThat(product.getThumbnailUrl()).isEqualTo("https://img.test/2.jpg");
    }

    @Test
    @DisplayName("저장된 것보다 작은 version의 수정 이벤트는 늦게 도착해도 반영하지 않는다")
    void olderUpdate_isIgnored() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));
        recommendKafkaListener.handle(event(UPDATED, 2L, "듀얼센스 화이트", null));

        recommendKafkaListener.handle(event(UPDATED, 1L, "듀얼센스 블랙", null));

        RecommendProduct product = product();
        assertThat(product.getVersion()).isEqualTo(2L);
        assertThat(product.getProductName()).isEqualTo("듀얼센스 화이트");
    }

    @Test
    @DisplayName("같은 version의 수정 이벤트는 반영한다 (이미지만 바꾼 수정은 version이 그대로다)")
    void sameVersionUpdate_isApplied() {
        recommendKafkaListener.handle(event(UPDATED, 2L, "듀얼센스", null));

        recommendKafkaListener.handle(event(UPDATED, 2L, "듀얼센스", "https://img.test/3.jpg"));

        RecommendProduct product = product();
        assertThat(product.getVersion()).isEqualTo(2L);
        assertThat(product.getThumbnailUrl()).isEqualTo("https://img.test/3.jpg");
    }

    @Test
    @DisplayName("등록 이벤트를 받은 적 없는 상품도 수정 이벤트로 복사본을 만든다")
    void updateWithoutCreated_createsCopy() {
        recommendKafkaListener.handle(event(UPDATED, 4L, "듀얼센스", null));

        RecommendProduct product = product();
        assertThat(product.getVersion()).isEqualTo(4L);
        assertThat(product.getProductName()).isEqualTo("듀얼센스");
        assertThat(product.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("삭제 이벤트를 받으면 행을 지우지 않고 삭제 표시와 함께 마지막 상품 정보와 version을 반영한다")
    void deleted_marksCopyDeletedWithLastInfo() {
        recommendKafkaListener.handle(event(CREATED, 0L, "듀얼센스", null));

        recommendKafkaListener.handle(event(DELETED, 1L, "듀얼센스 미개봉", null));

        RecommendProduct product = product();
        assertThat(product.getDeleted()).isTrue();
        assertThat(product.getVersion()).isEqualTo(1L);
        assertThat(product.getProductName()).isEqualTo("듀얼센스 미개봉");
    }

    @Test
    @DisplayName("등록 이벤트를 받은 적 없는 상품의 삭제 이벤트로 삭제 표시된 복사본을 만든다")
    void deleteWithoutCreated_createsDeletedCopy() {
        recommendKafkaListener.handle(event(DELETED, 1L, "듀얼센스", null));

        RecommendProduct product = product();
        assertThat(product.getDeleted()).isTrue();
        assertThat(product.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("저장된 것보다 작은 version의 삭제 이벤트는 반영하지 않는다")
    void olderDelete_isIgnored() {
        recommendKafkaListener.handle(event(UPDATED, 3L, "듀얼센스", null));

        recommendKafkaListener.handle(event(DELETED, 2L, "듀얼센스", null));

        RecommendProduct product = product();
        assertThat(product.getDeleted()).isFalse();
        assertThat(product.getVersion()).isEqualTo(3L);
    }

    private RecommendProduct product() {
        return recommendProductRepository.findById(PRODUCT_ID).orElseThrow();
    }

    private static ProductChangedEvent event(ProductChangeType changeType, long version, String productName, String thumbnailUrl) {
        return new ProductChangedEvent(
                PRODUCT_ID, changeType, version, 20L, productName, "ACCESSORY", "SONY", null,
                10000L, null, LocalDateTime.of(2026, 11, 12, 10, 0), thumbnailUrl, LocalDateTime.of(2026, 11, 10, 9, 0)
        );
    }
}
