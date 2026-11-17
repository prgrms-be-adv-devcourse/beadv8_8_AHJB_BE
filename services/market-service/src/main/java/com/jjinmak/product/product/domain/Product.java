package com.jjinmak.product.product.domain;

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

    // 경매 이벤트로 복제한 경매 상태와 결과 ('내 상품' 목록용)
    // 컬럼 추가 전 상품은 컬럼이 모두 null이라 JPA가 null로 채운다. → auctionState() 로 접근한다.
    @Embedded
    private ProductAuctionState auctionState;

    private LocalDateTime deletedAt;

    // 수정 기간 종료 시점에 판매자 수정과 경매 요청이 동시에 일어나는 것을 막는다.
    @Version
    private Long version;

    private Product(ProductMember seller, String name, ProductCategory category,
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
        this.auctionState = ProductAuctionState.scheduled();
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

    public static Product create(ProductMember seller, String name, ProductCategory category,
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
    public static Product createDutch(ProductMember admin, String name, ProductCategory category,
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

    // 경매를 요청했지만 아직 경매 소식(시작·결과)을 하나도 받지 못했으면 요청을 다시 보낼 수 있다.
    public boolean isAuctionResendable() {
        return deletedAt == null
                && auctionRequestedAt != null
                && currentAuctionId == null
                && getAuctionStatus() == ProductAuctionStatus.SCHEDULED;
    }

    // 경매 컨텍스트에서 경매가 시작되면 현재 회차의 경매 id와 종료 시각을 기록한다.
    public void startAuction(Long auctionId, LocalDateTime endAt) {
        ProductAuctionState state = auctionState(); // currentAuctionId를 바꾸기 전에 기존 상태를 확정한다
        this.currentAuctionId = auctionId;
        state.start(endAt);
    }

    public void markAuctionSold(Long auctionId) {
        if (isOtherAuction(auctionId)) {
            return;
        }
        auctionState().markSold();
    }

    public void markAuctionFailed(Long auctionId, ProductAuctionFailureReason reason) {
        if (isOtherAuction(auctionId)) {
            return;
        }
        auctionState().markFailed(reason);
    }

    // 경매 상태. 컬럼 추가 전 상품은 경매 id 유무로 추정한다.
    public ProductAuctionStatus getAuctionStatus() {
        if (auctionState == null || auctionState.getStatus() == null) {
            return currentAuctionId == null ? ProductAuctionStatus.SCHEDULED : ProductAuctionStatus.IN_PROGRESS;
        }
        return auctionState.getStatus();
    }

    public LocalDateTime getAuctionEndAt() {
        return auctionState == null ? null : auctionState.getEndAt();
    }

    public ProductAuctionFailureReason getAuctionFailureReason() {
        return auctionState == null ? null : auctionState.getFailureReason();
    }

    // 경매 요청 전이고 시작 예정 시각 전이면 판매자가 수정/삭제할 수 있다.
    public boolean isEditable(LocalDateTime now) {
        return auctionRequestedAt == null && now.isBefore(auctionScheduledAt);
    }

    // 다른 경매의 이벤트면 무시한다. (경매 id가 아직 없으면 시작 이벤트 처리 전이라 받아들인다)
    private boolean isOtherAuction(Long auctionId) {
        return currentAuctionId != null && !currentAuctionId.equals(auctionId);
    }

    private ProductAuctionState auctionState() {
        if (auctionState == null || auctionState.getStatus() == null) {
            auctionState = ProductAuctionState.restore(getAuctionStatus());
        }
        return auctionState;
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
