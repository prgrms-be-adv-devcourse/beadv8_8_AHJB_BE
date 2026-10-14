package com.jjinmak.back.boundedContext.settlement.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import com.jjinmak.back.shared.settlement.dto.SettlementDto;
import com.jjinmak.back.shared.settlement.dto.SettlementItemDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_settlement_seller_status", columnList = "seller_id, status"),
        @Index(name = "idx_settlement_status_payout_date", columnList = "status, payout_date")
})
public class Settlement extends BaseIdAndTime {

    private static final long FEE_RATE_PERCENT = 10;
    private static final int PAYOUT_DAY_OF_MONTH = 15;

    @NotNull
    @Column(unique = true)
    private Long orderId;

    @NotNull
    private Long sellerId;

    @NotNull
    private Long productId;

    @NotNull
    private String productName;

    @NotNull
    private Long winningPrice;

    @NotNull
    private Long deliveryFee;

    @NotNull
    private Long feeAmount;

    @NotNull
    private Long settlementAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private SettlementStatus status;

    @NotNull
    private LocalDateTime confirmedAt;

    @NotNull
    private LocalDate payoutDate;

    private LocalDateTime completedAt;

    @Version
    private Long version;

    /**
     * 구매확정된 주문으로 정산서를 만든다. 수수료·정산 금액·지급일은 이 시점에 계산해서 고정한다.
     * orderId, sellerId, productId, winningPrice, deliveryFee, confirmedAt은 주문의 구매확정 이벤트 값이고,
     * productName은 상품 도메인에서 조회한 구매확정 시점의 상품명이다.
     */
    public Settlement(Long orderId, Long sellerId, Long productId, String productName,
                      Long winningPrice, Long deliveryFee, LocalDateTime confirmedAt) {
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.productName = productName;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
        this.feeAmount = winningPrice * FEE_RATE_PERCENT / 100;
        this.settlementAmount = winningPrice - feeAmount + deliveryFee;
        this.status = SettlementStatus.PENDING;
        this.confirmedAt = confirmedAt;
        this.payoutDate = YearMonth.from(confirmedAt).plusMonths(1).atDay(PAYOUT_DAY_OF_MONTH);
    }

    public boolean isSeller(Long memberId) {
        return sellerId.equals(memberId);
    }

    public boolean isPayable(LocalDate today) {
        return status == SettlementStatus.PENDING && !payoutDate.isAfter(today);
    }

    public void complete() {
        if (status != SettlementStatus.PENDING) {
            throw new BusinessException(SettlementErrorCode.ALREADY_COMPLETED);
        }
        this.status = SettlementStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public SettlementDto dto() {
        return new SettlementDto(
                getId(),
                orderId,
                sellerId,
                settlementAmount,
                feeAmount
        );
    }

    public SettlementItemDto itemDto() {
        return new SettlementItemDto(
                getId(),
                productName,
                settlementAmount,
                status,
                payoutDate
        );
    }
}
