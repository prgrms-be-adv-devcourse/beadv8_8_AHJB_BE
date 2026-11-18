package com.jjinmak.boundedcontext.product.product.domain;

import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;
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

    // 네덜란드식은 플랫폼(관리자)이 직접 여는 이벤트라 판매자 정책 동의 대신 이 값으로 남긴다.
    public static final String PLATFORM_POLICY_VERSION = "PLATFORM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MarketMember seller;

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

    // 현재 경매 회차 (1부터, 재등록하면 1씩 늘어난다)
    @Column(nullable = false)
    private int round;

    // 현재 회차의 경매 id. 경매가 생성되면 채워진다. (null이면 아직 경매 시작 전)
    // 경매 상태·결과는 경매 컨텍스트에만 있다. 상품 화면은 ProductAuctionStateReader로 읽는다.
    private Long currentAuctionId;

    private LocalDateTime deletedAt;

    // 수정 기간 종료 시점에 판매자 수정과 경매 요청이 동시에 일어나는 것을 막는다.
    @Version
    private Long version;

    private Product(MarketMember seller, String name, ProductCategory category,
                    Manufacturer manufacturer, String manufacturerEtc, String description,
                    ShippingFeeType shippingFeeType, Long shippingFee, boolean bundleShipping,
                    String policyVersion, LocalDateTime now,
                    ProductAuctionTerms auctionTerms, LocalDateTime auctionScheduledAt) {
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
        this.auctionScheduledAt = auctionScheduledAt;
        this.round = 1;
    }

    // 등록 시각 기준 경매 시작 예정 시각 (등록 전 경매 조건 검증에도 사용)
    public static LocalDateTime scheduleAuctionAt(LocalDateTime registeredAt) {
        return registeredAt.plus(EDITABLE_PERIOD);
    }

    // 제조사가 기타(ETC)일 때만 직접 입력한 이름을 저장한다. (기타인데 비어 있으면 예외)
    // Manufacturer는 공용 enum(event-contracts)이라 상품 전용 예외를 던질 수 없어서 여기서 검증한다.
    private static String resolveManufacturerEtc(Manufacturer manufacturer, String manufacturerEtc) {
        if (manufacturer != Manufacturer.ETC) {
            return null;
        }
        if (manufacturerEtc == null || manufacturerEtc.isBlank()) {
            throw new BusinessException(ProductErrorCode.DIRECT_INPUT_REQUIRED);
        }
        return manufacturerEtc.strip();
    }

    public static Product create(MarketMember seller, String name, ProductCategory category,
                                 Manufacturer manufacturer, String manufacturerEtc, String description,
                                 ShippingFeeType shippingFeeType, Long customShippingFee, boolean bundleShipping,
                                 String policyVersion, LocalDateTime now, ProductAuctionTerms auctionTerms) {
        return new Product(
                seller, name, category,
                manufacturer, resolveManufacturerEtc(manufacturer, manufacturerEtc), description,
                shippingFeeType, shippingFeeType.resolveFee(customShippingFee), bundleShipping,
                policyVersion, now, auctionTerms, scheduleAuctionAt(now)
        );
    }

    // 네덜란드식 이벤트 상품. 관리자가 오픈 시각을 직접 정한다. (지금보다 뒤여야 한다)
    // 오픈 시각이 되면 영국식과 같은 스케줄러가 경매를 요청한다.
    public static Product createDutch(MarketMember admin, String name, ProductCategory category,
                                      Manufacturer manufacturer, String manufacturerEtc, String description,
                                      ShippingFeeType shippingFeeType, Long customShippingFee, boolean bundleShipping,
                                      LocalDateTime now, ProductAuctionTerms dutchTerms, LocalDateTime openAt) {
        if (!dutchTerms.isDutch()) {
            throw new IllegalArgumentException("네덜란드식 경매 조건이 아닙니다.");
        }
        if (!openAt.isAfter(now)) {
            throw new BusinessException(ProductErrorCode.INVALID_DUTCH_OPEN_AT);
        }
        return new Product(
                admin, name, category,
                manufacturer, resolveManufacturerEtc(manufacturer, manufacturerEtc), description,
                shippingFeeType, shippingFeeType.resolveFee(customShippingFee), bundleShipping,
                PLATFORM_POLICY_VERSION, now, dutchTerms, openAt
        );
    }

    public boolean isDutch() {
        return auctionTerms.isDutch();
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

    // 판매자가 상품을 다음 회차 경매에 다시 올린다. 지금 바로 시작하고, 경매 기간은 이전 회차와 같다.
    // 이전 회차가 다시 열 수 있는 상태(유찰·미결제 무효)인지는 경매 컨텍스트가 확인한다.
    // 반환값: 새 회차의 종료 시각
    public LocalDateTime relist(Long sellerId, LocalDateTime now) {
        if (deletedAt != null) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!isSoldBy(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_OWNER);
        }
        if (isDutch()) {
            throw new BusinessException(ProductErrorCode.DUTCH_NOT_RELISTABLE);
        }
        if (currentAuctionId == null) {
            throw new BusinessException(ProductErrorCode.NOT_RELISTABLE);
        }

        // 직접 지정한 종료 시각(CUSTOM)도 이전 회차의 기간 길이로 바꿔 쓴다.
        Duration auctionLength = Duration.between(auctionScheduledAt, auctionTerms.resolveEndAt(auctionScheduledAt));

        this.round++;
        this.auctionScheduledAt = now;
        this.auctionRequestedAt = now;
        this.currentAuctionId = null;
        return now.plus(auctionLength);
    }

    // 경매가 생성되면 현재 회차의 경매 id를 기록한다.
    public void startAuction(Long auctionId) {
        this.currentAuctionId = auctionId;
    }

    // 경매 요청 전이고 시작 예정 시각 전이면 판매자가 수정/삭제할 수 있다.
    public boolean isEditable(LocalDateTime now) {
        return auctionRequestedAt == null && now.isBefore(auctionScheduledAt);
    }

    public boolean isSoldBy(Long memberId) {
        return memberId != null && seller.getId().equals(memberId);
    }

    // 직접입력 배송비일 때만 입력한 금액을 돌려준다. (수정 화면 표시용)
    public Long getCustomShippingFee() {
        return shippingFeeType == ShippingFeeType.CUSTOM ? shippingFee : null;
    }

    // 판매자 수정/삭제 API로 바꿀 수 있는지 ('내 상품' 화면의 수정 버튼용)
    // 네덜란드식은 관리자가 여는 이벤트라 판매자 API로는 바꿀 수 없다.
    public boolean isSellerEditable(LocalDateTime now) {
        return !isDutch() && isEditable(now);
    }

    // 본인 상품이고, 경매 요청 전이면서 시작 예정 시각 전일 때만 수정/삭제할 수 있다.
    // 네덜란드식 이벤트 상품은 판매자 수정/삭제 API로 바꿀 수 없다. (관리자 수정/취소는 별도 기능)
    public void validateEditable(Long sellerId, LocalDateTime now) {
        if (!seller.getId().equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_OWNER);
        }
        if (isDutch()) {
            throw new BusinessException(ProductErrorCode.DUTCH_NOT_SELLER_EDITABLE);
        }
        if (!isEditable(now)) {
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
            this.manufacturerEtc = resolveManufacturerEtc(targetManufacturer, targetEtc);
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
