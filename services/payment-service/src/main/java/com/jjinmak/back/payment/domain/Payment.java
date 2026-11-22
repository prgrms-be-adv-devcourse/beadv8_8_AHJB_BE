package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.PgMethod;
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
                @UniqueConstraint(name = Payment.UK_ORDER_GROUP, columnNames = "order_group_id"),
                @UniqueConstraint(name = Payment.UK_IDEMPOTENCY, columnNames = "idempotency_key")
        },
        indexes = @Index(name = "idx_payment_holder", columnList = "holder_id, created_at")
)
@Getter
@NoArgsConstructor
public class Payment extends BaseIdAndTime {

    // 주문 그룹당 Payment 는 하나. 같은 주문 그룹의 첫 결제 요청이 동시에 오면 늦은 쪽이 이 제약에 걸린다 (PaymentCreateRaceSupport)
    public static final String UK_ORDER_GROUP = "uk_payment_order_group";
    public static final String UK_IDEMPOTENCY = "uk_payment_idempotency";

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

    // 결제창에서 결제할 금액. 예치금 결제면 부족분 충전 금액, 직접 결제면 pgAmount. 0 이면 결제창 없이 결제된다
    @Column(nullable = false)
    private long chargeAmount;

    // 사용자가 고른 결제수단 (포인트로 내고 남은 금액을 낼 수단)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PayMethod payMethod;

    // 결제 금액 구성. 세 값의 합 == totalPrice
    @Column(nullable = false)
    private long pointAmount;

    @Column(nullable = false)
    private long depositAmount;     // 예치금으로 낸 금액. 부족분을 충전해서 낸 금액도 포함한다

    @Column(nullable = false)
    private long pgAmount;          // 결제창에서 직접 결제한 금액. 예치금을 거치지 않는다

    // 직접 결제가 실제로 승인된 결제수단 (토스 승인 응답의 method. 예: 계좌이체, 카드, 간편결제). 직접 결제가 아니면 null
    // 사용자가 고른 수단(payMethod)과 다를 수 있다. 적립처럼 수단에 따라 달라지는 계산은 이 값을 기준으로 한다
    @Column(length = 30)
    private String pgMethod;

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
        this.payMethod = PayMethod.DEPOSIT;
        this.pointAmount = 0L;
        this.depositAmount = totalPrice;
        this.pgAmount = 0L;
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


    // 결제 요청 시점에 결제 금액 구성(결제수단 · 포인트 · 예치금 · 직접 결제 · 결제창 금액)을 정한다. 계산은 PaymentPlanner 가 한다.
    // 결제창을 거쳐야 하면 CHARGING, 아니면 PENDING 에 머문 채 바로 홀딩으로 간다.
    // 사용자가 결제창을 닫고 다시 주문하기를 누르면(CHARGING 상태) 잔액이나 고른 수단이 달라졌을 수 있으므로 다시 정한다.
    public void decide(PaymentPlan plan) {
        requireStatus(PENDING, CHARGING);
        if (plan.pointAmount() < 0 || plan.depositAmount() < 0 || plan.pgAmount() < 0 || plan.chargeAmount() < 0) {
            throw new BusinessException(INVALID_PAYMENT_AMOUNT);
        }
        if (plan.totalPrice() != totalPrice || (plan.depositAmount() > 0 && plan.pgAmount() > 0)) {
            throw new BusinessException(PAYMENT_PLAN_MISMATCH);
        }
        this.payMethod = plan.payMethod();
        this.pointAmount = plan.pointAmount();
        this.depositAmount = plan.depositAmount();
        this.pgAmount = plan.pgAmount();
        this.chargeAmount = plan.chargeAmount();
        this.status = plan.needsPgWindow() ? CHARGING : PENDING;
    }

    // 전액 예치금 결제에서 충전할 금액만 정한다 (포인트 · 직접 결제 없음)
    public void decideCharge(long chargeAmount) {
        decide(new PaymentPlan(PayMethod.DEPOSIT, 0L, totalPrice, 0L, chargeAmount, Math.min(chargeAmount, totalPrice), false));
    }

    public PaymentShare getShare() {
        return new PaymentShare(pointAmount, depositAmount, pgAmount);
    }

    public boolean needsCharge() {
        return chargeAmount > 0;
    }

    // 결제창에서 낸 돈이 예치금을 거치지 않고 주문 대금이 되는 결제인지
    public boolean isDirectPayment() {
        return pgAmount > 0;
    }

    // 직접 결제가 실제로 계좌이체로 승인됐는지. 고른 수단이 아니라 토스가 알려준 실제 수단으로 판단한다 (포인트 적립 대상 판별)
    public boolean isPaidByTransfer() {
        return PgMethod.TRANSFER.matches(pgMethod);
    }

    // 지갑 차감 + 주문별 Holding 생성이 끝난 뒤 호출. 이미 PAID 면 아무 것도 하지 않는다 (멱등)
    public void markPaid(LocalDateTime paidAt) {
        if (isPaid()) return;
        requireStatus(PENDING, CHARGING);
        this.status = PAID;
        this.paidAt = paidAt;
    }

    // 직접 결제 승인 완료. 토스가 알려준 실제 결제수단을 함께 남긴다
    public void markPaid(LocalDateTime paidAt, String pgMethod) {
        if (isPaid()) return;
        markPaid(paidAt);
        this.pgMethod = pgMethod;
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
