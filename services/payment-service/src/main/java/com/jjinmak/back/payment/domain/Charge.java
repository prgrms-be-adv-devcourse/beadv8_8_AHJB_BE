package com.jjinmak.back.payment.domain;

import com.jjinmak.back.payment.app.support.DepositConsumer;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.PgMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static com.jjinmak.back.payment.domain.enums.ChargePurpose.FOR_PAYMENT;
import static com.jjinmak.back.payment.domain.enums.ChargePurpose.MANUAL;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.*;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.*;

@Entity
@Table(
        name = "charge",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_charge_pg_order", columnNames = "pg_order_id"),
                @UniqueConstraint(name = "uk_charge_payment_key", columnNames = "payment_key")
        },
        indexes = {
                @Index(name = "idx_charge_wallet", columnList = "wallet_id, created_at"),
                @Index(name = "idx_charge_status", columnList = "status, created_at")
        }
)
@Getter
@NoArgsConstructor
public class Charge extends BaseIdAndTime {

    private static final String PG_ORDER_ID_PREFIX = "CHG-";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Wallet wallet;

    @Column(nullable = false)
    private Long holderId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long remainingAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ChargePurpose purpose;

    @ManyToOne(fetch = FetchType.LAZY)
    private Payment payment;

    @Column(nullable = false, length = 64)
    private String pgOrderId;

    @Column(length = 200)
    private String paymentKey;

    @Column(length = 30)
    private String method;

    @Column(length = 500)
    private String receiptUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ChargeStatus status;

    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    private long version;

    private LocalDateTime approvedAt;

    private LocalDateTime chargedAt;

    private LocalDateTime usedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime abortedAt;

    @Column(length = 50)
    private String failureCode;

    @Column(length = 510)
    private String failureMessage;


    private Charge(Wallet wallet, Long holderId, long amount, ChargePurpose purpose, Payment payment) {
        if (amount <= 0) {
            throw new BusinessException(INVALID_CHARGE_AMOUNT);
        }
        if (purpose == FOR_PAYMENT && payment == null) {
            throw new BusinessException(CHARGE_PAYMENT_REQUIRED);
        }
        this.wallet = wallet;
        this.holderId = holderId;
        this.amount = amount;
        this.purpose = purpose;
        this.payment = payment;
        this.pgOrderId = generatePgOrderId();
        this.status = REQUESTED;
        this.remainingAmount = 0L;
    }

    // 사용자가 직접 예치금을 충전
    public static Charge requestManual(Wallet wallet, Long holderId, long amount) {
        return new Charge(wallet, holderId, amount, MANUAL, null);
    }

    // 주문 결제 중 부족분만 충전. 금액은 Payment.chargeAmount 를 그대로 쓴다
    public static Charge requestForPayment(Wallet wallet, Payment payment) {
        return new Charge(wallet, payment.getHolderId(), payment.getChargeAmount(), FOR_PAYMENT, payment);
    }

    // 토스 orderId 규칙(6~64자, 영문·숫자·-·_)에 맞는 유일한 값. "CHG-" + 32자 hex = 36자
    private static String generatePgOrderId() {
        return PG_ORDER_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }


    public void validateAmount(long requestedAmount) {
        if (this.amount != requestedAmount) {
            throw new BusinessException(CHARGE_AMOUNT_MISMATCH);
        }
    }

    // 결제창을 열 때 써야 하는 결제수단
    public PgMethod getRequiredMethod() {
        return purpose.requiredMethod();
    }

    // 토스 승인 응답의 결제수단이 이 충전에 허용된 수단인지
    public boolean allowsMethod(String tossMethod) {
        return getRequiredMethod().matches(tossMethod);
    }

    // 토스 승인 성공 + 지갑 CHARGE 기록 완료. 이미 CHARGED 이상이면 아무 것도 하지 않는다 (멱등)
    public void markCharged(String paymentKey, LocalDateTime approvedAt, LocalDateTime chargedAt) {
        markCharged(paymentKey, approvedAt, chargedAt, null, null);
    }

    // 토스 승인 응답의 결제수단·영수증 링크까지 함께 남긴다
    public void markCharged(String paymentKey, LocalDateTime approvedAt, LocalDateTime chargedAt,
                            String method, String receiptUrl) {
        if (status != REQUESTED) {
            if (Objects.equals(paymentKey, this.paymentKey)) return;
            throw new BusinessException(INVALID_CHARGE_STATUS);
        }
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.chargedAt = chargedAt;
        this.method = method;
        this.receiptUrl = receiptUrl;
        this.status = CHARGED;
        this.remainingAmount = this.amount;
    }

    //예치금이 빠져나갈 때 이 충전의 미사용 금액을 소비한다 (DepositConsumer 가 호출).
    // 남은 금액까지만 소비하고 실제로 소비한 금액을 돌려준다.
    public long consume(long requested) {
        if (requested <= 0 || status != CHARGED) return 0L;
        long consumed = Math.min(requested, remainingAmount);
        this.remainingAmount -= consumed;
        return consumed;
    }

    // FOR_PAYMENT 충전이 해당 주문의 홀딩에 소비됨. 같은 트랜잭션에서 홀딩 직후 호출
    // 최소 충전 금액 때문에 부족분보다 많이 충전했다면 남은 금액은 예치금에 그대로 있고, 이 충전의 미사용 금액으로는 세지 않는다
    // (USED 충전은 취소할 수 없으므로 남은 돈은 환불금처럼 충전 건에 묶이지 않은 예치금이 된다)
    public void markUsed(LocalDateTime usedAt) {
        if (status == USED) return;
        if (purpose != FOR_PAYMENT) {
            throw new BusinessException(INVALID_CHARGE_STATUS);
        }
        requireStatus(CHARGED);
        this.status = USED;
        this.usedAt = usedAt;
        this.remainingAmount = 0L;
    }

    // 충전 취소 (토스 취소 + 지갑 CHARGE_CANCEL 완료 후)
    public void markCanceled(LocalDateTime canceledAt) {
        if (status == CANCELED) return;
        if (!isCancelable()) {
            throw new BusinessException(CHARGE_NOT_CANCELABLE);
        }
        this.status = CANCELED;
        this.canceledAt = canceledAt;
        this.remainingAmount = 0L;
    }

    // 토스 승인 실패 또는 만료. 토스 failure 객체의 code / message 를 남긴다
    public void markAborted(LocalDateTime abortedAt, String failureCode, String failureMessage) {
        if (status == ABORTED) return;
        requireStatus(REQUESTED);
        this.status = ABORTED;
        this.abortedAt = abortedAt;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    // 돈이 들어와 있고 한 푼도 쓰이지 않은 충전만 취소할 수 있다 (선입선출 기준). 일부라도 쓰였으면 영구히 불가
    public boolean isCancelable() {
        return status == CHARGED && remainingAmount == amount;
    }

    // 이 충전으로 들어온 돈 중 이미 쓰인 금액
    public long getUsedAmount() {
        return isCharged() || status == CANCELED ? amount - remainingAmount : 0L;
    }

    // 선입선출 소비 대상인지: 돈이 들어와 있고 남은 금액이 있는 건
    public boolean hasRemaining() {
        return status == CHARGED && remainingAmount > 0;
    }

    // 돈은 들어왔는데 주문 결제가 끝나지 않은 건인지 (복구 대상 판별)
    public boolean isChargedButUnused() {
        return purpose == FOR_PAYMENT && status == CHARGED;
    }

    public boolean isForPayment() {
        return purpose == FOR_PAYMENT;
    }

    public boolean isCharged() {
        return status == CHARGED || status == USED;
    }

    private void requireStatus(ChargeStatus... allowed) {
        for (ChargeStatus s : allowed) {
            if (this.status == s) return;
        }
        throw new BusinessException(INVALID_CHARGE_STATUS);
    }
}
