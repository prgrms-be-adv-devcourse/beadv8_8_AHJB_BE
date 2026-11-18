package com.jjinmak.back.payment.domain;

import com.jjinmak.back.payment.domain.enums.PgMethod;
import com.jjinmak.back.payment.domain.enums.WalletType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 충전 건이 허용하는 결제수단. 예치금으로 들어오는 충전(수동 충전 · 부족분 충전)은 계좌이체만 받는다.
 */
class ChargeMethodTest {

    private final Wallet wallet = new Wallet(null, WalletType.USER);

    private Charge manual() {
        return Charge.requestManual(wallet, 1L, 10_000);
    }

    private Charge forPayment() {
        Payment payment = Payment.create(100L, 1L, wallet, 30_000);
        payment.decideCharge(10_000);
        return Charge.requestForPayment(wallet, payment);
    }

    @Test
    @DisplayName("수동 충전과 부족분 충전 모두 결제창을 계좌이체(TRANSFER)로 열어야 한다")
    void requiredMethodIsTransfer() {
        assertThat(manual().getRequiredMethod()).isEqualTo(PgMethod.TRANSFER);
        assertThat(forPayment().getRequiredMethod()).isEqualTo(PgMethod.TRANSFER);
    }

    @Test
    @DisplayName("토스 승인 응답의 결제수단이 계좌이체일 때만 허용한다")
    void allowsOnlyTransfer() {
        assertThat(manual().allowsMethod("계좌이체")).isTrue();
        assertThat(forPayment().allowsMethod("계좌이체")).isTrue();

        assertThat(manual().allowsMethod("카드")).isFalse();
        assertThat(manual().allowsMethod("간편결제")).isFalse();
        assertThat(manual().allowsMethod("가상계좌")).isFalse();
        assertThat(manual().allowsMethod("휴대폰")).isFalse();
        assertThat(forPayment().allowsMethod("카드")).isFalse();
    }

    @Test
    @DisplayName("결제수단을 알 수 없으면(null · 빈 값) 허용하지 않는다")
    void unknownMethodIsNotAllowed() {
        assertThat(manual().allowsMethod(null)).isFalse();
        assertThat(manual().allowsMethod("")).isFalse();
    }

    @Test
    @DisplayName("결제창 method 값(TRANSFER)으로 와도 계좌이체로 본다")
    void acceptsSdkName() {
        assertThat(manual().allowsMethod("TRANSFER")).isTrue();
        assertThat(manual().allowsMethod("CARD")).isFalse();
    }
}
