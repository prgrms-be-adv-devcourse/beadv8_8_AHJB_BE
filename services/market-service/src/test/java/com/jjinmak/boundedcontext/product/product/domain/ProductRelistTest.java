package com.jjinmak.boundedcontext.product.product.domain;

import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRelistTest {

    private static final Long SELLER_ID = 100L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 20, 12, 0);
    private static final LocalDateTime SCHEDULED_AT = CREATED_AT.plus(Product.EDITABLE_PERIOD);
    private static final LocalDateTime RELIST_AT = SCHEDULED_AT.plusDays(5);

    private Product 상품(AuctionDuration duration, LocalDateTime customEndAt) {
        ProductAuctionTerms terms = ProductAuctionTerms.create(10_000L, null, duration, customEndAt, SCHEDULED_AT);
        return Product.create(new ProductMember(SELLER_ID, "판매자"), "스위치", ProductCategory.CONSOLE,
                Manufacturer.NINTENDO, null, "설명", ShippingFeeType.FREE, null, false,
                "v1", CREATED_AT, terms);
    }

    private Product 경매가_열렸던_상품(AuctionDuration duration, LocalDateTime customEndAt) {
        Product product = 상품(duration, customEndAt);
        product.requestAuction(SCHEDULED_AT);
        product.startAuction(10L, product.getAuctionTerms().resolveEndAt(SCHEDULED_AT));
        return product;
    }

    @Test
    void 다음_회차로_넘기고_지금부터_이전_회차와_같은_기간으로_연다() {
        Product product = 경매가_열렸던_상품(AuctionDuration.DAYS_3, null);

        LocalDateTime endAt = product.relist(SELLER_ID, RELIST_AT);

        assertThat(product.getAuctionRound()).isEqualTo(2);
        assertThat(product.getAuctionScheduledAt()).isEqualTo(RELIST_AT);
        assertThat(product.getCurrentAuctionId()).isNull();
        assertThat(product.getAuctionStatus()).isEqualTo(ProductAuctionStatus.SCHEDULED);
        assertThat(endAt).isEqualTo(RELIST_AT.plusDays(3));
        assertThat(product.isAuctionRequestable(RELIST_AT)).isFalse();   // 스케줄러가 다시 열지 않는다
    }

    @Test
    void 종료_시각을_직접_지정했으면_그_기간_길이를_그대로_쓴다() {
        Product product = 경매가_열렸던_상품(AuctionDuration.CUSTOM, SCHEDULED_AT.plusDays(2).plusHours(6));

        LocalDateTime endAt = product.relist(SELLER_ID, RELIST_AT);

        assertThat(endAt).isEqualTo(RELIST_AT.plusDays(2).plusHours(6));
    }

    @Test
    void 새_회차_경매가_생성되면_진행_중이_된다() {
        Product product = 경매가_열렸던_상품(AuctionDuration.DAYS_1, null);
        LocalDateTime endAt = product.relist(SELLER_ID, RELIST_AT);

        product.startAuction(20L, endAt);

        assertThat(product.getCurrentAuctionId()).isEqualTo(20L);
        assertThat(product.getAuctionStatus()).isEqualTo(ProductAuctionStatus.IN_PROGRESS);
    }

    @Test
    void 판매자_본인이_아니면_재등록할_수_없다() {
        Product product = 경매가_열렸던_상품(AuctionDuration.DAYS_1, null);

        assertThatThrownBy(() -> product.relist(999L, RELIST_AT))
                .extracting("errorCode").isEqualTo(ProductErrorCode.NOT_PRODUCT_OWNER);
    }

    @Test
    void 네덜란드식_이벤트_상품은_재등록할_수_없다() {
        Product product = Product.createDutch(new ProductMember(SELLER_ID, "관리자"), "스위치", ProductCategory.CONSOLE,
                Manufacturer.NINTENDO, null, "설명", ShippingFeeType.FREE, null, false, CREATED_AT,
                ProductAuctionTerms.createDutch(300_000L, 150_000L, 2, 60), SCHEDULED_AT);
        product.requestAuction(SCHEDULED_AT);
        product.startAuction(10L, null);

        assertThatThrownBy(() -> product.relist(SELLER_ID, RELIST_AT))
                .extracting("errorCode").isEqualTo(ProductErrorCode.DUTCH_NOT_RELISTABLE);
    }

    @Test
    void 경매가_열린_적_없으면_재등록할_수_없다() {
        Product product = 상품(AuctionDuration.DAYS_1, null);

        assertThatThrownBy(() -> product.relist(SELLER_ID, RELIST_AT))
                .extracting("errorCode").isEqualTo(ProductErrorCode.NOT_RELISTABLE);
    }
}
