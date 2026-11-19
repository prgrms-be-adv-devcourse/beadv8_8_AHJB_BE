package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.PaymentQuoteDto;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.ChargePolicy;
import com.jjinmak.back.payment.domain.PaymentPlanner;
import com.jjinmak.back.payment.domain.PointPolicy;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.WalletType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.INVALID_PAYMENT_AMOUNT;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.POINT_USE_OUT_OF_RANGE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 결제 전 견적: 결제수단 · 포인트 사용액에 따른 안내 금액과 예상 적립 포인트. 최소 충전 금액 10,000 / 1회 한도 2,000,000 / 적립률 1% 기준.
 */
class PaymentQuoteUseCaseTest {

    private static final long HOLDER_ID = 1L;

    private PaymentQuoteUseCase useCase;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);
        WalletSupport walletSupport = mock(WalletSupport.class);
        when(walletSupport.getWalletByHolderId(HOLDER_ID)).thenReturn(wallet);
        useCase = new PaymentQuoteUseCase(walletSupport, new PaymentPlanner(new ChargePolicy(10_000, 2_000_000)),
                new PointPolicy(1));
    }

    @Test
    @DisplayName("결제수단 · 포인트를 고르지 않으면 전액 예치금 결제 견적이다")
    void defaultIsDeposit() {
        wallet.deposit(29_700);

        PaymentQuoteDto quote = useCase.quote(HOLDER_ID, 30_000);

        assertThat(quote.payMethod()).isEqualTo(PayMethod.DEPOSIT);
        assertThat(quote.walletUseAmount()).isEqualTo(29_700);
        assertThat(quote.shortage()).isEqualTo(300);
        assertThat(quote.needsCharge()).isTrue();
        assertThat(quote.chargeAmount()).isEqualTo(10_000);
        assertThat(quote.pgAmount()).isEqualTo(10_000);
        assertThat(quote.pgMethod()).isEqualTo("TRANSFER");
        assertThat(quote.balanceAfterPayment()).isEqualTo(9_700);
        assertThat(quote.chargeLimitExceeded()).isFalse();
    }

    @Test
    @DisplayName("포인트 + 예치금: 포인트를 뺀 금액을 예치금으로 내고, 충분하면 결제창이 필요 없다")
    void pointAndDeposit() {
        wallet.deposit(25_000);
        wallet.depositPoint(8_000);

        PaymentQuoteDto quote = useCase.quote(HOLDER_ID, 30_000, "DEPOSIT", 8_000);

        assertThat(quote.pointBalance()).isEqualTo(8_000);
        assertThat(quote.pointUseAmount()).isEqualTo(8_000);
        assertThat(quote.walletUseAmount()).isEqualTo(22_000);
        assertThat(quote.needsCharge()).isFalse();
        assertThat(quote.pgAmount()).isZero();
        assertThat(quote.pgMethod()).isNull();
        assertThat(quote.balanceAfterPayment()).isEqualTo(3_000);
    }

    @Test
    @DisplayName("포인트 + 카드 직접 결제: 예치금은 쓰지 않고, 포인트를 뺀 금액을 카드 결제창에서 결제한다")
    void pointAndCard() {
        wallet.deposit(25_000);
        wallet.depositPoint(8_000);

        PaymentQuoteDto quote = useCase.quote(HOLDER_ID, 30_000, "CARD", 8_000);

        assertThat(quote.payMethod()).isEqualTo(PayMethod.CARD);
        assertThat(quote.walletUseAmount()).isZero();
        assertThat(quote.shortage()).isZero();
        assertThat(quote.needsCharge()).isFalse();
        assertThat(quote.chargeAmount()).isZero();
        assertThat(quote.pgAmount()).isEqualTo(22_000);
        assertThat(quote.pgMethod()).isEqualTo("CARD");
        assertThat(quote.balanceAfterPayment()).isEqualTo(25_000);       // 예치금은 그대로
    }

    @Test
    @DisplayName("계좌이체 직접 결제가 1회 한도를 넘으면 오류 대신 chargeLimitExceeded 로 알린다")
    void transferOverLimitIsFlagged() {
        PaymentQuoteDto transfer = useCase.quote(HOLDER_ID, 2_500_000, "TRANSFER", 0);
        assertThat(transfer.chargeLimitExceeded()).isTrue();
        assertThat(transfer.pgMethod()).isEqualTo("TRANSFER");

        PaymentQuoteDto card = useCase.quote(HOLDER_ID, 2_500_000, "CARD", 0);
        assertThat(card.chargeLimitExceeded()).isFalse();
    }

    // ===== 예상 적립 포인트 =====

    @Test
    @DisplayName("예치금 결제: 예치금으로 내는 금액(충전해서 내는 금액 포함)의 1% 가 예상 적립 포인트다")
    void expectedEarnForDeposit() {
        wallet.deposit(7_000);

        PaymentQuoteDto quote = useCase.quote(HOLDER_ID, 30_000);

        assertThat(quote.expectedEarnPoint()).isEqualTo(300);
        assertThat(quote.earnRatePercent()).isEqualTo(1);
    }

    @Test
    @DisplayName("포인트로 내는 금액에는 적립되지 않는다")
    void pointUseIsNotEarned() {
        wallet.deposit(30_000);
        wallet.depositPoint(8_000);

        assertThat(useCase.quote(HOLDER_ID, 30_000, "DEPOSIT", 8_000).expectedEarnPoint()).isEqualTo(220);   // 22,000 의 1%
        assertThat(useCase.quote(HOLDER_ID, 30_000, "TRANSFER", 8_000).expectedEarnPoint()).isEqualTo(220);
    }

    @Test
    @DisplayName("계좌이체 직접 결제는 적립되고, 카드·간편결제 직접 결제는 적립되지 않는다")
    void transferEarnsButCardDoesNot() {
        assertThat(useCase.quote(HOLDER_ID, 30_000, "TRANSFER", 0).expectedEarnPoint()).isEqualTo(300);
        assertThat(useCase.quote(HOLDER_ID, 30_000, "CARD", 0).expectedEarnPoint()).isZero();
    }

    @Test
    @DisplayName("배송비를 보내면 배송비 몫을 빼고 계산한다")
    void deliveryFeeIsExcluded() {
        wallet.deposit(50_000);
        wallet.depositPoint(11_000);

        // 총액 33,000 중 배송비 3,000. 예치금 33,000 → 낙찰가 몫 30,000 → 300
        assertThat(useCase.quote(HOLDER_ID, 33_000, "DEPOSIT", 0, 3_000).expectedEarnPoint()).isEqualTo(300);
        // 포인트 11,000 사용 → 예치금 22,000 → 낙찰가 몫 20,000 → 200
        assertThat(useCase.quote(HOLDER_ID, 33_000, "DEPOSIT", 11_000, 3_000).expectedEarnPoint()).isEqualTo(200);
    }

    @Test
    @DisplayName("배송비가 음수이거나 결제 금액 이상이면 INVALID_PAYMENT_AMOUNT")
    void invalidDeliveryFee() {
        assertThatThrownBy(() -> useCase.quote(HOLDER_ID, 30_000, "DEPOSIT", 0, -1))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INVALID_PAYMENT_AMOUNT);
        assertThatThrownBy(() -> useCase.quote(HOLDER_ID, 30_000, "DEPOSIT", 0, 30_000))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INVALID_PAYMENT_AMOUNT);
    }

    @Test
    @DisplayName("포인트 사용액이 결제 금액을 넘거나 포인트 잔액보다 많으면 결제 요청과 같은 오류를 돌려준다")
    void invalidPointUse() {
        wallet.depositPoint(1_000);

        assertThatThrownBy(() -> useCase.quote(HOLDER_ID, 30_000, "DEPOSIT", 30_001))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(POINT_USE_OUT_OF_RANGE);
        assertThatThrownBy(() -> useCase.quote(HOLDER_ID, 30_000, "DEPOSIT", 1_001))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(INSUFFICIENT_POINT);
    }
}
