package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseIdAndTime {

    // 등록 후 이 기간 동안은 판매자가 수정/삭제할 수 있고, 지나면 경매로 넘어간다.
    public static final Duration EDITABLE_PERIOD = Duration.ofHours(1);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ProductMember seller;

    @Column(name = "product_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private Manufacturer manufacturer;

    // manufacturer가 ETC일 때만 값이 있다.
    private String manufacturerEtc;

    @Column(name = "product_description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ShippingFeeType shippingFeeType;

    @Column(nullable = false)
    private Long shippingFee;

    @Column(nullable = false)
    private boolean bundleShipping;

    @Column(nullable = false)
    private int wishCount;

    // 판매자가 동의한 판매 정책 버전과 동의 시각
    @Column(nullable = false)
    private String policyVersion;

    @Column(nullable = false)
    private LocalDateTime policyAgreedAt;

    @Embedded
    private ProductAuctionTerms auctionTerms;

    // 경매 컨텍스트로 경매 생성을 요청한 시각 (null이면 아직 수정 기간)
    private LocalDateTime auctionRequestedAt;

    private LocalDateTime deletedAt;

    // 수정 기간 종료 시점에 판매자 수정과 경매 요청이 동시에 일어나는 것을 막는다.
    @Version
    private Long version;

    private Product(ProductMember seller, String name, ProductCategory category,
                    Manufacturer manufacturer, String manufacturerEtc, String description,
                    ShippingFeeType shippingFeeType, Long shippingFee, boolean bundleShipping,
                    String policyVersion, LocalDateTime policyAgreedAt, ProductAuctionTerms auctionTerms) {
        this.seller = seller;
        this.name = name;
        this.category = category;
        this.manufacturer = manufacturer;
        this.manufacturerEtc = manufacturerEtc;
        this.description = description;
        this.shippingFeeType = shippingFeeType;
        this.shippingFee = shippingFee;
        this.bundleShipping = bundleShipping;
        this.wishCount = 0;
        this.policyVersion = policyVersion;
        this.policyAgreedAt = policyAgreedAt;
        this.auctionTerms = auctionTerms;
    }

    public static Product create(ProductMember seller, String name, ProductCategory category,
                                 Manufacturer manufacturer, String manufacturerEtc, String description,
                                 ShippingFeeType shippingFeeType, Long customShippingFee, boolean bundleShipping,
                                 String policyVersion, LocalDateTime now, ProductAuctionTerms auctionTerms) {
        return new Product(
                seller, name, category,
                manufacturer, manufacturer.resolveEtcName(manufacturerEtc), description,
                shippingFeeType, shippingFeeType.resolveFee(customShippingFee), bundleShipping,
                policyVersion, now, auctionTerms
        );
    }

    // 삭제되지 않았고, 아직 경매를 요청하지 않았고, 수정 기간이 지났으면 경매를 요청할 수 있다.

    public boolean isAuctionRequestable(LocalDateTime now) {
        return deletedAt == null
                && auctionRequestedAt == null
                && !now.isBefore(getCreatedAt().plus(EDITABLE_PERIOD));
    }

    public void requestAuction(LocalDateTime now) {
        this.auctionRequestedAt = now;
    }
}
