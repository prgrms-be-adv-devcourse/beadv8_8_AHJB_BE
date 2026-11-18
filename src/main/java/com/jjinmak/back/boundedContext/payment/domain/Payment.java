package com.jjinmak.back.boundedContext.payment.domain;

import com.jjinmak.back.boundedContext.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jjinmak.back.boundedContext.payment.domain.enums.PaymentStatus.*;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.*;

/**
 * 주문 묶음(OrderGroup) 1건에 대한 결제 기록. 주문 그룹당 1개.
 *
 * 토스 결제 정보를 담지 않는다. 토스와 주고받은 것은 Charge 에만 있다.
 * 전액 예치금 결제든 부족분 충전 후 결제든, 주문 결제는 항상 이 엔티티를 거친다.
 *
 * 흐름: PENDING(주문 이벤트 수신) → [CHARGING(부족분 PG 충전 중)] → PAID(지갑 차감 + 주문별 Holding 생성) / FAILED
 *
 * - totalPrice   : 주문 총액 = 아이템 금액 합 = 홀딩 총액
 * - chargeAmount : 결제 요청 시점의 잔액으로 계산한 부족분. 0 이면 PG 를 타지 않는다
 * - idempotencyKey : 결제 요청·승인 콜백이 중복 도착해도 한 번만 처리하기 위한 키. orderGroupId 로 결정적으로 만든다
 */
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

    /**
     * OrderCreatePaymentEvent 를 받아 Payment 를 만든다.
     * 아이템은 addItem 으로 추가한 뒤 validateItems 로 합계를 검증한다.
     */
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

    /** 아이템 금액 합 == totalPrice 를 검증한다. 아이템을 다 넣은 뒤 한 번 호출 */
    public void validateItems() {
        if (items.isEmpty()) {
            throw new BusinessException(PAYMENT_ITEMS_EMPTY);
        }
        long sum = items.stream().mapToLong(PaymentOrderItem::getAmount).sum();
        if (sum != totalPrice) {
            throw new BusinessException(PAYMENT_AMOUNT_MISMATCH);
        }
    }

    /**
     * 결제 요청 시점의 지갑 잔액으로 부족분을 계산한다.
     * 부족분이 있으면 CHARGING, 없으면 PENDING 에 머문 채 바로 홀딩으로 간다.
     * 사용자가 결제창을 닫고 다시 주문하기를 누르면(CHARGING 상태) 잔액이 달라졌을 수 있으므로 다시 계산한다.
     *
     * @return 부족분 (0 이면 PG 불필요)
     */
    public long decideCharge(long walletBalance) {
        requireStatus(PENDING, CHARGING);
        long shortage = Math.max(0L, totalPrice - walletBalance);
        this.chargeAmount = shortage;
        this.status = shortage > 0 ? CHARGING : PENDING;
        return shortage;
    }

    public boolean needsCharge() {
        return chargeAmount > 0;
    }

    /** 지갑 차감 + 주문별 Holding 생성이 끝난 뒤 호출. 이미 PAID 면 아무 것도 하지 않는다 (멱등) */
    public void markPaid(LocalDateTime paidAt) {
        if (isPaid()) return;
        requireStatus(PENDING, CHARGING);
        this.status = PAID;
        this.paidAt = paidAt;
    }

    /** PG 승인 실패 등으로 결제를 끝내지 못했을 때. 이미 FAILED 면 아무 것도 하지 않는다 (멱등) */
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

    /** 주문 1건 환불 완료 표시. 돈의 이동은 Holding/원장이 담당하고 여기는 시각만 남긴다 */
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

    /** 모든 주문이 환불됐는지. Payment 단위의 환불 시각은 이걸로 파생한다 */
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
