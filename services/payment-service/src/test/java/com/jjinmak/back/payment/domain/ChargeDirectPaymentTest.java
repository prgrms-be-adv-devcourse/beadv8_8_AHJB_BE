package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PgMethod;
import com.jjinmak.back.payment.domain.enums.WalletType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_NOT_CANCELABLE;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.INVALID_CANCEL_AMOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 직접 결제 건(DIRECT_PAYMENT): 승인, 주문 단위 부분 취소, 결제 계획과의 일치.
 */
class ChargeDirectPaymentTest {

    private static final long TOTAL = 50_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);

    private Wallet wallet;
    private Payment payment;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);
        payment = Payment.create(100L, 1L, wallet, TOTAL);
    }

    // 포인트 pointAmount + 나머지 직접 결제
    private Charge directCharge(PayMethod method, long pointAmount) {
        long pg = TOTAL - pointAmount;
        payment.decide(new PaymentPlan(method, pointAmount, 0L, pg, pg, 0L, false));
        return Charge.requestDirectPayment(wallet, payment, method.pgMethod());
    }

    @Test
    @DisplayName("직접 결제 건은 고른 수단으로 결제창을 열고, 금액은 포인트를 뺀 나머지다")
    void requestDirectPayment() {
        Charge charge = directCharge(PayMethod.CARD, 8_000);

        assertThat(charge.isDirectPayment()).isTrue();
        assertThat(charge.isBoundToPayment()).isTrue();
        assertThat(charge.getAmount()).isEqualTo(42_000);
        assertThat(charge.getRequiredMethod()).isEqualTo(PgMethod.CARD);
    }

    @Test
    @DisplayName("직접 결제는 실제 결제수단을 가리지 않고 받는다 (계좌이체를 골랐는데 카드로 결제해도 거절하지 않는다)")
    void allowsAnyMethod() {
        Charge charge = directCharge(PayMethod.TRANSFER, 0);

        assertThat(charge.allowsMethod("계좌이체")).isTrue();
        assertThat(charge.allowsMethod("카드")).isTrue();
        assertThat(charge.allowsMethod("간편결제")).isTrue();
    }

    @Test
    @DisplayName("승인되면 예치금을 거치지 않으므로 CHARGED 없이 USED 가 되고 미사용 금액은 0 이다")
    void markPaidDirectly() {
        Charge charge = directCharge(PayMethod.TRANSFER, 0);

        charge.markPaidDirectly("pk-1", NOW, NOW, "계좌이체", "https://receipt");

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
        assertThat(charge.getRemainingAmount()).isZero();
        assertThat(charge.hasRemaining()).isFalse();
        assertThat(charge.isCancelable()).isFalse();                    // 사용자 충전 취소 대상이 아니다
        assertThat(charge.getMethod()).isEqualTo("계좌이체");
        assertThat(charge.getPgBalanceAmount()).isEqualTo(TOTAL);

        charge.markPaidDirectly("pk-1", NOW, NOW, "계좌이체", "https://receipt");   // 같은 결제 키로 다시 와도 그대로 (멱등)
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
    }

    @Test
    @DisplayName("예치금 충전 건은 직접 결제로 승인 처리할 수 없다")
    void depositChargeCannotBePaidDirectly() {
        payment.decideCharge(10_000);
        Charge charge = Charge.requestForPayment(wallet, payment);

        assertThatThrownBy(() -> charge.markPaidDirectly("pk-1", NOW, NOW, "계좌이체", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("주문 몫만큼 부분 취소하면 취소 누계가 쌓이고, 전부 취소되면 CANCELED 가 된다")
    void cancelPartially() {
        Charge charge = directCharge(PayMethod.CARD, 0);
        charge.markPaidDirectly("pk-1", NOW, NOW, "카드", null);

        charge.cancelPartially(20_000, NOW);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
        assertThat(charge.getCanceledAmount()).isEqualTo(20_000);
        assertThat(charge.getPgBalanceAmount()).isEqualTo(30_000);

        charge.cancelPartially(30_000, NOW);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELED);
        assertThat(charge.getCanceledAmount()).isEqualTo(TOTAL);
        assertThat(charge.getCanceledAt()).isEqualTo(NOW);
        assertThat(charge.getPgBalanceAmount()).isZero();
    }

    @Test
    @DisplayName("남은 금액보다 많이 취소하거나 0 이하를 취소할 수 없다")
    void cancelAmountOutOfRange() {
        Charge charge = directCharge(PayMethod.CARD, 0);
        charge.markPaidDirectly("pk-1", NOW, NOW, "카드", null);
        charge.cancelPartially(20_000, NOW);

        assertThatThrownBy(() -> charge.cancelPartially(30_001, NOW))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INVALID_CANCEL_AMOUNT);
        assertThatThrownBy(() -> charge.cancelPartially(0, NOW))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INVALID_CANCEL_AMOUNT);
        assertThat(charge.getCanceledAmount()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("승인되지 않은 직접 결제 건과 예치금 충전 건은 부분 취소할 수 없다")
    void onlyPaidDirectChargeIsPartiallyCancelable() {
        Charge notPaid = directCharge(PayMethod.CARD, 0);
        assertThatThrownBy(() -> notPaid.cancelPartially(1_000, NOW))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(CHARGE_NOT_CANCELABLE);

        Charge manual = Charge.requestManual(wallet, 1L, 30_000);
        manual.markCharged("pk-2", NOW, NOW);
        assertThatThrownBy(() -> manual.cancelPartially(1_000, NOW))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(CHARGE_NOT_CANCELABLE);
    }

    @Test
    @DisplayName("결제수단이나 포인트를 바꿔 계획이 달라지면 예전 계획으로 만든 건은 더 이상 맞지 않는다")
    void matchesPaymentPlan() {
        Charge transfer = directCharge(PayMethod.TRANSFER, 0);
        assertThat(transfer.matchesPaymentPlan()).isTrue();

        Charge card = directCharge(PayMethod.CARD, 0);                  // 수단 변경 (금액은 같다)
        assertThat(transfer.matchesPaymentPlan()).isFalse();
        assertThat(card.matchesPaymentPlan()).isTrue();

        Charge cardWithPoint = directCharge(PayMethod.CARD, 5_000);     // 포인트 사용 → 금액 변경
        assertThat(card.matchesPaymentPlan()).isFalse();
        assertThat(cardWithPoint.matchesPaymentPlan()).isTrue();

        payment.decideCharge(45_000);                                   // 예치금 결제로 변경. 충전 금액이 직접 결제 금액과 같아도 맞지 않는다
        assertThat(cardWithPoint.matchesPaymentPlan()).isFalse();
        assertThat(Charge.requestForPayment(wallet, payment).matchesPaymentPlan()).isTrue();
    }
}
