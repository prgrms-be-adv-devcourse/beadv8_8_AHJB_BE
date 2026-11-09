package com.jjinmak.search.domain;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangeType;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 이벤트 순서가 뒤바뀌거나 중복돼도 검색 문서가 올바른 상태가 되는지 확인한다. (ES·Kafka 없이 실행)
class ProductSearchDocumentTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long AUCTION_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);

    @Test
    @DisplayName("저장된 버전보다 오래된 상품 이벤트는 무시한다")
    void ignoreOlderProductVersion() {
        ProductSearchDocument document = ProductSearchDocument.empty(PRODUCT_ID);
        document.applyProductChange(productChanged(ProductChangeType.UPDATED, 1L, "닌텐도 스위치 OLED"), NOW);

        boolean applied = document.applyProductChange(productChanged(ProductChangeType.CREATED, 0L, "닌텐도 스위치"), NOW);

        assertThat(applied).isFalse();
        assertThat(document.getProductName()).isEqualTo("닌텐도 스위치 OLED");
        assertThat(document.getProductVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 버전의 상품 이벤트는 반영한다 (이미지만 바꾼 수정은 버전이 그대로)")
    void applySameProductVersion() {
        ProductSearchDocument document = ProductSearchDocument.empty(PRODUCT_ID);
        document.applyProductChange(productChanged(ProductChangeType.UPDATED, 1L, "닌텐도 스위치"), NOW);

        boolean applied = document.applyProductChange(productChanged(ProductChangeType.UPDATED, 1L, "닌텐도 스위치 새 사진"), NOW);

        assertThat(applied).isTrue();
        assertThat(document.getProductName()).isEqualTo("닌텐도 스위치 새 사진");
    }

    @Test
    @DisplayName("경매 시작이 상품 등록보다 먼저 와도 진행 중 상태와 상품 정보가 모두 남는다")
    void auctionStartedBeforeProductCreated() {
        ProductSearchDocument document = ProductSearchDocument.empty(PRODUCT_ID);

        document.applyAuctionStarted(auctionStarted(AUCTION_ID), NOW);
        document.applyProductChange(productChanged(ProductChangeType.CREATED, 0L, "플레이스테이션5"), NOW);

        assertThat(document.getStatus()).isEqualTo(ProductSearchStatus.IN_PROGRESS);
        assertThat(document.getAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(document.getProductName()).isEqualTo("플레이스테이션5");
        assertThat(document.getManufacturerName()).isEqualTo("소니");
    }

    @Test
    @DisplayName("낙찰 후 미결제·환불로 무산되면 SOLD에서 FAILED로 바뀐다")
    void soldThenFailed() {
        ProductSearchDocument document = startedDocument();
        document.applyAuctionWon(auctionWon(AUCTION_ID), NOW);

        boolean applied = document.applyAuctionFailed(auctionFailed(AUCTION_ID), NOW);

        assertThat(applied).isTrue();
        assertThat(document.getStatus()).isEqualTo(ProductSearchStatus.FAILED);
    }

    @Test
    @DisplayName("이미 끝난 경매는 늦게 온 시작·낙찰 이벤트로 되돌아가지 않는다")
    void endedAuctionStaysEnded() {
        ProductSearchDocument document = startedDocument();
        document.applyAuctionFailed(auctionFailed(AUCTION_ID), NOW);

        document.applyAuctionStarted(auctionStarted(AUCTION_ID), NOW);
        boolean wonApplied = document.applyAuctionWon(auctionWon(AUCTION_ID), NOW);

        assertThat(wonApplied).isFalse();
        assertThat(document.getStatus()).isEqualTo(ProductSearchStatus.FAILED);
    }

    @Test
    @DisplayName("다른 경매의 이벤트는 무시한다")
    void ignoreOtherAuction() {
        ProductSearchDocument document = startedDocument();

        boolean applied = document.applyAuctionWon(auctionWon(999L), NOW);

        assertThat(applied).isFalse();
        assertThat(document.getStatus()).isEqualTo(ProductSearchStatus.IN_PROGRESS);
        assertThat(document.getAuctionId()).isEqualTo(AUCTION_ID);
    }

    @Test
    @DisplayName("삭제 이벤트를 받으면 문서를 지우지 않고 삭제 표시만 한다")
    void markDeleted() {
        ProductSearchDocument document = ProductSearchDocument.empty(PRODUCT_ID);
        document.applyProductChange(productChanged(ProductChangeType.CREATED, 0L, "닌텐도 스위치"), NOW);

        document.applyProductChange(productChanged(ProductChangeType.DELETED, 1L, "닌텐도 스위치"), NOW);

        assertThat(document.isDeleted()).isTrue();
        assertThat(document.getProductName()).isEqualTo("닌텐도 스위치");
    }

    // ── 테스트 데이터 ──

    private ProductSearchDocument startedDocument() {
        ProductSearchDocument document = ProductSearchDocument.empty(PRODUCT_ID);
        document.applyProductChange(productChanged(ProductChangeType.CREATED, 0L, "플레이스테이션5"), NOW);
        document.applyAuctionStarted(auctionStarted(AUCTION_ID), NOW);
        return document;
    }

    private ProductChangedEvent productChanged(ProductChangeType type, Long version, String name) {
        return new ProductChangedEvent(
                PRODUCT_ID, type, version, 10L,
                name, "CONSOLE", "SONY", "소니",
                300_000L, null,
                NOW.plusHours(1), null, NOW
        );
    }

    private AuctionStartedEvent auctionStarted(Long auctionId) {
        return new AuctionStartedEvent(auctionId, PRODUCT_ID, 300_000L, null, NOW.plusHours(1), NOW.plusDays(3));
    }

    private AuctionWonEvent auctionWon(Long auctionId) {
        return new AuctionWonEvent(auctionId, PRODUCT_ID, 20L, 10L, 350_000L, 0L, NOW.plusDays(3));
    }

    private AuctionFailedEvent auctionFailed(Long auctionId) {
        return new AuctionFailedEvent(auctionId, PRODUCT_ID, "UNPAID", NOW.plusDays(4));
    }
}
