package com.jjinmak.back.boundedContext.payment.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.INVALID_PAYMENT_AMOUNT;

/**
 * Payment(주문 묶음 결제) 안의 주문 1건.
 * OrderCreatePaymentEvent 의 orderItem 을 그대로 복사해 둔다. 승인 시점에 이 값으로 주문별 Holding 을 만든다.
 *
 * 환불은 주문 단위로 일어나므로 환불 시각은 여기에 둔다. (Payment 전체의 환불 여부는 아이템들로부터 파생)
 * order 컨텍스트 테이블에는 FK 를 걸지 않는다.
 */
@Entity
@Table(
        name = "payment_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_item_order", columnNames = "order_id")
)
@Getter
@NoArgsConstructor
public class PaymentOrderItem extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Payment payment;

    /** order 컨텍스트의 주문 id. FK 없음 */
    @Column(nullable = false)
    private Long orderId;

    /** 판매자 회원 id (= WalletMember id). FK 없음 */
    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private long winningPrice;

    @Column(nullable = false)
    private long deliveryFee;

    private LocalDateTime refundedAt;

    PaymentOrderItem(Payment payment, Long orderId, Long sellerId, long winningPrice, long deliveryFee) {
        if (winningPrice <= 0 || deliveryFee < 0) {
            throw new BusinessException(INVALID_PAYMENT_AMOUNT);
        }
        this.payment = payment;
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.winningPrice = winningPrice;
        this.deliveryFee = deliveryFee;
    }

    /** 이 주문 몫의 결제 금액 (낙찰가 + 배송비). Holding.amount 와 같아야 한다 */
    public long getAmount() {
        return winningPrice + deliveryFee;
    }

    /** 환불 완료 표시. 이미 환불됐으면 아무 것도 하지 않는다 (멱등) */
    public void markRefunded(LocalDateTime refundedAt) {
        if (isRefunded()) return;
        this.refundedAt = refundedAt;
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }
}
