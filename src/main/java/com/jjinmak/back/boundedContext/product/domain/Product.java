package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.BusinessException;
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

    // 등록 후 이 기간 동안은 판매자가 수정/삭제할 수 있고, 지나면 경매가 시작된다.
    // TODO: 프론트 연동 배포 테스트용으로 5분. 테스트 끝나면 Duration.ofHours(1)로 되돌리기
    public static final Duration EDITABLE_PERIOD = Duration.ofMinutes(5);

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

    // 경매 시작 예정 시각 (등록 시각 + 수정 기간). 이 시각 전까지 수정/삭제할 수 있다.
    @Column(nullable = false)
    private LocalDateTime auctionScheduledAt;

    // 경매 컨텍스트로 경매 생성을 요청한 시각 (null이면 아직 요청 전)
    private LocalDateTime auctionRequestedAt;

    // 경매 회차 (재등록 시 증가)
    @Column(nullable = false)
    private int round;

    // 현재 회차의 경매 id. 경매 시작 이벤트로 채워진다. (null이면 아직 경매 시작 전)
    private Long currentAuctionId;

    private LocalDateTime deletedAt;

    // 수정 기간 종료 시점에 판매자 수정과 경매 요청이 동시에 일어나는 것을 막는다.
    @Version
    private Long version;

    private Product(ProductMember seller, String name, ProductCategory category,
                    Manufacturer manufacturer, String manufacturerEtc, String description,
                    ShippingFeeType shippingFeeType, Long shippingFee, boolean bundleShipping,
                    String policyVersion, LocalDateTime now, ProductAuctionTerms auctionTerms) {
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
        this.policyAgreedAt = now;
        this.auctionTerms = auctionTerms;
        this.auctionScheduledAt = scheduleAuctionAt(now);
        this.round = 1;
    }

    // 등록 시각 기준 경매 시작 예정 시각 (등록 전 경매 조건 검증에도 사용)
    public static LocalDateTime scheduleAuctionAt(LocalDateTime registeredAt) {
        return registeredAt.plus(EDITABLE_PERIOD);
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

    // 삭제되지 않았고, 아직 경매를 요청하지 않았고, 시작 예정 시각이 되었으면 경매를 요청할 수 있다.
    public boolean isAuctionRequestable(LocalDateTime now) {
        return deletedAt == null
                && auctionRequestedAt == null
                && !now.isBefore(auctionScheduledAt);
    }

    public void requestAuction(LocalDateTime now) {
        this.auctionRequestedAt = now;
    }

    // 경매 컨텍스트에서 경매가 시작되면 현재 회차의 경매 id를 기록한다.
    public void startAuction(Long auctionId) {
        this.currentAuctionId = auctionId;
    }

    public boolean isSoldBy(Long memberId) {
        return memberId != null && seller.getId().equals(memberId);
    }

    // 직접입력 배송비일 때만 입력한 금액을 돌려준다. (수정 화면 표시용)
    public Long getCustomShippingFee() {
        return shippingFeeType == ShippingFeeType.CUSTOM ? shippingFee : null;
    }

    // 본인 상품이고, 경매 요청 전이면서 시작 예정 시각 전일 때만 수정/삭제할 수 있다.
    public void validateEditable(Long sellerId, LocalDateTime now) {
        if (!seller.getId().equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_OWNER);
        }
        if (auctionRequestedAt != null || !now.isBefore(auctionScheduledAt)) {
            throw new BusinessException(ProductErrorCode.NOT_EDITABLE);
        }
    }

    // 값이 있는 항목만 수정한다. (null이면 기존 값 유지)
    public void update(String name, ProductCategory category,
                       Manufacturer manufacturer, String manufacturerEtc, String description,
                       ShippingFeeType shippingFeeType, Long customShippingFee, Boolean bundleShipping,
                       ProductAuctionTerms auctionTerms) {
        if (name != null) {
            this.name = name;
        }
        if (category != null) {
            this.category = category;
        }
        if (manufacturer != null || manufacturerEtc != null) {
            Manufacturer targetManufacturer = manufacturer != null ? manufacturer : this.manufacturer;
            String targetEtc = manufacturerEtc != null ? manufacturerEtc : this.manufacturerEtc;
            this.manufacturer = targetManufacturer;
            this.manufacturerEtc = targetManufacturer.resolveEtcName(targetEtc);
        }
        if (description != null) {
            this.description = description;
        }
        if (shippingFeeType != null) {
            this.shippingFeeType = shippingFeeType;
            this.shippingFee = shippingFeeType.resolveFee(customShippingFee);
        }
        if (bundleShipping != null) {
            this.bundleShipping = bundleShipping;
        }
        if (auctionTerms != null) {
            this.auctionTerms = auctionTerms;
        }
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
