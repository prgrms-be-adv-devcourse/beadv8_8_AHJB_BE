package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.PaymentStatus.*;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.*;


@Entity
@Table(
        name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_order_group", columnNames = "order_group_id"),
                @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotency_key")
        },
        indexes = @Index(name = "idx_payment_holder", columnList = "holder_id, created_at")
)
@Getter
@NoArgsConstructor
public class Payment extends BaseIdAndTime {

    private static final String IDEMPOTENCY_PREFIX = "ORDER_GROUP:";

    @Column(nullable = false)
    private Long orderGroupId;

    @Column(nullable = false)
    private Long holderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet buyerWallet;

    @Column(nullable = false)
    private long totalPrice;

    @Column(nullable = false)
    private long chargeAmount;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime paidAt;

    private LocalDateTime failedAt;

    @Column(length = 200)
    private String failureReason;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentOrderItem> items = new ArrayList<>();


    private Payment(Long orderGroupId, Long holderId, Wallet buyerWallet, long totalPrice) {
        if (totalPrice <= 0) {
            throw new BusinessException(INVALID_PAYMENT_AMOUNT);
        }
        this.orderGroupId = orderGroupId;
        this.holderId = holderId;
        this.buyerWallet = buyerWallet;
        this.totalPrice = totalPrice;
        this.chargeAmount = 0L;
        this.idempotencyKey = idempotencyKeyOf(orderGroupId);
        this.status = PENDING;
    }

    public static Payment create(Long orderGroupId, Long holderId, Wallet buyerWallet, long totalPrice) {
        return new Payment(orderGroupId, holderId, buyerWallet, totalPrice);
    }

    public static String idempotencyKeyOf(Long orderGroupId) {
        return IDEMPOTENCY_PREFIX + orderGroupId;
    }

    public PaymentOrderItem addItem(Long orderId, Long sellerId, long winningPrice, long deliveryFee) {
        requireStatus(PENDING);
        PaymentOrderItem item = new PaymentOrderItem(this, orderId, sellerId, winningPrice, deliveryFee);
        this.items.add(item);
        return item;
    }

    //  아이템 금액 합 == totalPrice 를 검증한다. 아이템을 다 넣은 뒤 한 번 호출
    public void validateItems() {
        if (items.isEmpty()) {
            throw new BusinessException(PAYMENT_ITEMS_EMPTY);
        }
        long sum = items.stream().mapToLong(PaymentOrderItem::getAmount).sum();
        if (sum != totalPrice) {
            throw new BusinessException(PAYMENT_AMOUNT_MISMATCH);
        }
    }

    // 이 잔액으로 결제하면 모자라는 금액. 모자라지 않으면 0
    public long shortageOf(long walletBalance) {
        return Math.max(0L, totalPrice - walletBalance);
    }


    // 결제 요청 시점에 PG 로 충전할 금액을 정한다. 금액은 부족분에 충전 정책(최소 충전 금액)을 적용해 호출하는 쪽이 계산한다.
    // 충전할 금액이 있으면 CHARGING, 없으면 PENDING 에 머문 채 바로 홀딩으로 간다.
    // 사용자가 결제창을 닫고 다시 주문하기를 누르면(CHARGING 상태) 잔액이 달라졌을 수 있으므로 다시 정한다.
    public void decideCharge(long chargeAmount) {
        requireStatus(PENDING, CHARGING);
        if (chargeAmount < 0) {
            throw new BusinessException(INVALID_PAYMENT_AMOUNT);
        }
        this.chargeAmount = chargeAmount;
        this.status = chargeAmount > 0 ? CHARGING : PENDING;
    }

    // 충전하기 전부터 있던 예치금에서 나간 금액. 충전액이 총액 이상이면(최소 충전 금액으로 올려 충전) 0
    public long getWalletUsedAmount() {
        return Math.max(0L, totalPrice - chargeAmount);
    }

    public boolean needsCharge() {
        return chargeAmount > 0;
    }

    // 지갑 차감 + 주문별 Holding 생성이 끝난 뒤 호출. 이미 PAID 면 아무 것도 하지 않는다 (멱등)
    public void markPaid(LocalDateTime paidAt) {
        if (isPaid()) return;
        requireStatus(PENDING, CHARGING);
        this.status = PAID;
        this.paidAt = paidAt;
    }

    // PG 승인 실패 등으로 결제를 끝내지 못했을 때. 이미 FAILED 면 아무 것도 하지 않는다 (멱등)
    public void markFailed(LocalDateTime failedAt, String reason) {
        if (status == FAILED) return;
        requireStatus(PENDING, CHARGING);
        this.status = FAILED;
        this.failedAt = failedAt;
        this.failureReason = reason;
    }

    public boolean isFailed() {
        return status == FAILED;
    }

    // 주문 1건 환불 완료 표시. 돈의 이동은 Holding/원장이 담당하고 여기는 시각만 남긴다
    public void markItemRefunded(Long orderId, LocalDateTime refundedAt) {
        requireStatus(PAID);
        findItem(orderId).markRefunded(refundedAt);
    }

    public PaymentOrderItem findItem(Long orderId) {
        return items.stream()
                .filter(item -> item.getOrderId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(PAYMENT_ITEM_NOT_FOUND));
    }

    public List<PaymentOrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isPaid() {
        return status == PAID;
    }

    public boolean isCharging() {
        return status == CHARGING;
    }

    // 모든 주문이 환불됐는지. Payment 단위의 환불 시각은 이걸로 파생한다
    public boolean isAllRefunded() {
        return !items.isEmpty() && items.stream().allMatch(PaymentOrderItem::isRefunded);
    }

    private void requireStatus(PaymentStatus... allowed) {
        for (PaymentStatus s : allowed) {
            if (this.status == s) return;
        }
        throw new BusinessException(INVALID_PAYMENT_STATUS);
    }
}
