package com.jjinmak.back.boundedContext.wallet.domain;

import com.jjinmak.back.boundedContext.wallet.domain.enums.HoldingStatus;
import com.jjinmak.back.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 주문 1건당 1개. 결제 시 구매자 지갑에서 빠져나간 대금이
 * 판매자 정산 또는 구매자 환불 전까지 머무는 기록.
 * 실제 잔액은 ESCROW 시스템 지갑에 있고, Holding은 "그 중 어느 주문 몫이 얼마인지"를 나타낸다.
 * (HELD 상태 Holding의 amount 합계 == ESCROW 지갑 balance 가 항상 성립해야 한다.)
 */
@Entity
@Table(
        name = "holding",
        uniqueConstraints = @UniqueConstraint(name = "uk_holding_order", columnNames = "order_id")
)
@Getter
@NoArgsConstructor
public class Holding extends BaseManualIdAndTime {


    /** order 컨텍스트의 주문 id. FK 없음 */
    @Column(nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet buyerWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet sellerWallet;

    /** 구매자가 지불한 총액 */
    @Column(nullable = false)
    private long amount;

    /** 구매 확정 시 FEE 지갑으로 가는 금액. 정책 변경에 대비해 홀딩 시점 값으로 고정 */
    @Column(nullable = false)
    private long feeAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private HoldingStatus status;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    private Holding(Long orderId, Wallet buyerWallet, Wallet sellerWallet, long amount, long feeAmount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("홀딩 금액은 0보다 커야 합니다.");
        }
        if (feeAmount < 0 || feeAmount > amount) {
            throw new IllegalArgumentException("수수료는 0 이상, 홀딩 금액 이하여야 합니다.");
        }
        this.orderId = orderId;
        this.buyerWallet = buyerWallet;
        this.sellerWallet = sellerWallet;
        this.amount = amount;
        this.feeAmount = feeAmount;
        this.status = HoldingStatus.HELD;
    }

    public static Holding hold(Long orderId, Wallet buyerWallet, Wallet sellerWallet, long amount, long feeAmount) {
        return new Holding(orderId, buyerWallet, sellerWallet, amount, feeAmount);
    }

    /** 판매자에게 실제로 정산되는 금액 */
    public long getSettlementAmount() {
        return amount - feeAmount;
    }

    public void release() {
        requireHeld();
        this.status = HoldingStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }

    public void refund() {
        requireHeld();
        this.status = HoldingStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }

    private void requireHeld() {
        if (this.status != HoldingStatus.HELD) {
            throw new IllegalStateException("HELD 상태에서만 처리할 수 있습니다. 현재 상태=" + status);
        }
    }
}
