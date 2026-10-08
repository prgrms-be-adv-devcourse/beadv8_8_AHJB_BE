package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseIdAndTime {

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

    private LocalDateTime deletedAt;

    private Product(ProductMember seller, String name, ProductCategory category,
                    Manufacturer manufacturer, String manufacturerEtc, String description,
                    ShippingFeeType shippingFeeType, Long shippingFee, boolean bundleShipping,
                    String policyVersion, LocalDateTime policyAgreedAt) {
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
    }

    public static Product create(ProductMember seller, String name, ProductCategory category,
                                 Manufacturer manufacturer, String manufacturerEtc, String description,
                                 ShippingFeeType shippingFeeType, Long customShippingFee, boolean bundleShipping,
                                 String policyVersion, LocalDateTime now) {
        return new Product(
                seller, name, category,
                manufacturer, manufacturer.resolveEtcName(manufacturerEtc), description,
                shippingFeeType, shippingFeeType.resolveFee(customShippingFee), bundleShipping,
                policyVersion, now
        );
    }
}
