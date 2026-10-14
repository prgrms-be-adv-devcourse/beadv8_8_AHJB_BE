package com.jjinmak.back.boundedContext.wallet.domain;

import com.jjinmak.back.boundedContext.wallet.domain.enums.HoldingStatus;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.*;

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
public class Holding extends BaseIdAndTime {


    /** order 컨텍스트의 주문 id. FK 없음 */
    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet buyerWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet sellerWallet;

    /** 구매자가 지불한 총액 */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long winningPrice;

    /** 구매 확정 시 FEE 지갑으로 가는 금액. 정책 변경에 대비해 홀딩 시점 값으로 고정 */
    @Column(nullable = false)
    private long feeAmount;

    @Column(nullable = false)
    private long deliveryFee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private HoldingStatus status;

    private LocalDateTime releasedAt;

    private LocalDateTime refundedAt;

    private LocalDateTime confirmedAt;

    private Holding(Long orderId, Long paymentId, Wallet buyerWallet, Wallet sellerWallet, long winningPrice, long deliveryFee, long feeAmount) {
        if (winningPrice <= 0 || deliveryFee < 0) {
            throw new BusinessException(INVALID_FEE_AMOUNT);
        }
        if (feeAmount < 0 || feeAmount > winningPrice) {
            throw new BusinessException(INVALID_FEE_AMOUNT);
        }
        if (buyerWallet.getId().equals(sellerWallet.getId())) {
            throw new BusinessException(SAME_BUYER_AND_SELLER);
        }
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.buyerWallet = buyerWallet;
        this.sellerWallet = sellerWallet;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
        this.feeAmount = feeAmount;
        this.amount = winningPrice + deliveryFee;
        this.status = HoldingStatus.HELD;
    }

    public static Holding hold(Long orderId, Long paymentId, Wallet buyerWallet, Wallet sellerWallet, long winningPrice, long deliveryFee, long feeAmount) {
        return new Holding(orderId, paymentId, buyerWallet, sellerWallet, winningPrice, deliveryFee, feeAmount);
    }

    /** 판매자에게 실제로 정산되는 금액 */
    public long getSettlementAmount() {
        return amount - feeAmount;
    }

    public void confirm() {
        requireHeld();
        this.status = HoldingStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void release() {
        requireHeld();
        this.status = HoldingStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }

    public void refund(long requestedAmount) {
        requireHeld();
        if (this.amount != requestedAmount) {            // 전액 환불만 허용 — 도메인 규칙
            throw new BusinessException(REFUND_AMOUNT_MISMATCH);
        }
        this.status = HoldingStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }

    private void requireHeld() {
        switch (this.status) {
            case HELD -> {}
            case CONFIRMED -> throw new BusinessException(HOLDING_ALREADY_CONFIRMED);
            case RELEASED -> throw new BusinessException(HOLDING_ALREADY_RELEASED);
            case REFUNDED -> throw new BusinessException(HOLDING_ALREADY_REFUNDED);
        }
    }

    public boolean isRefunded() {
        return this.status == HoldingStatus.REFUNDED;
    }

    public boolean isConfirmed() {
        return this.status == HoldingStatus.CONFIRMED;
    }
}
