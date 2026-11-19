package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.support.ChargeRecoverySupport;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.TossWebhookEvent;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PgMethod;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.domain.enums.WebhookEventStatus;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.TossWebhookEventRepository;
import com.jjinmak.back.payment.out.toss.TossPaymentsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 직접 결제 건의 주문 환불(부분 취소) 뒤에 오는 토스 웹훅 처리.
 * 우리가 취소한 금액과 토스에 남은 금액이 맞으면 이미 반영된 것으로 넘기고, 다르면 외부 취소로 남긴다.
 */
class TossWebhookPartialCancelTest {

    private static final long EVENT_ID = 1L;
    private static final long AMOUNT = 50_000L;
    private static final String PAYMENT_KEY = "pk-direct";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);

    private TossWebhookProcessUseCase useCase;
    private TossPaymentsClient toss;

    private TossWebhookEvent event;
    private Charge charge;

    @BeforeEach
    void setUp() {
        Wallet wallet = new Wallet(null, WalletType.USER);
        Payment payment = Payment.create(100L, 1L, wallet, AMOUNT);
        payment.decide(new PaymentPlan(PayMethod.CARD, 0L, 0L, AMOUNT, AMOUNT, 0L, false));
        charge = Charge.requestDirectPayment(wallet, payment, PgMethod.CARD);
        charge.markPaidDirectly(PAYMENT_KEY, NOW, NOW, "카드", null);

        event = TossWebhookEvent.receive("PAYMENT_STATUS_CHANGED", PAYMENT_KEY, charge.getPgOrderId(), "{}");

        TossWebhookEventRepository eventRepository = mock(TossWebhookEventRepository.class);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        ChargeRepository chargeRepository = mock(ChargeRepository.class);
        when(chargeRepository.findByPgOrderId(charge.getPgOrderId())).thenReturn(Optional.of(charge));
        toss = mock(TossPaymentsClient.class);

        useCase = new TossWebhookProcessUseCase(eventRepository, chargeRepository, toss, mock(ChargeRecoverySupport.class));
    }

    // 토스가 알려주는 지금 상태: status 와 남은 금액
    private void tossSays(String status, long balanceAmount) {
        when(toss.findByPaymentKey(PAYMENT_KEY)).thenReturn(new TossPaymentResponse(
                PAYMENT_KEY, charge.getPgOrderId(), "찐막 주문 결제", status, AMOUNT, balanceAmount,
                "카드", null, "2026-11-18T10:00:00+09:00", null, null, null));
    }

    @Test
    @DisplayName("우리가 주문 환불로 부분 취소한 만큼 토스에도 빠져 있으면 이미 반영된 것으로 넘긴다")
    void ownPartialCancelIsIgnored() {
        charge.cancelPartially(33_000, NOW);
        tossSays("PARTIAL_CANCELED", 17_000);

        useCase.process(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
    }

    @Test
    @DisplayName("토스에 남은 금액이 우리가 아는 금액과 다르면 외부 취소로 보고 실패로 남긴다")
    void unknownPartialCancelIsFailed() {
        charge.cancelPartially(33_000, NOW);
        tossSays("PARTIAL_CANCELED", 10_000);                           // 우리가 모르는 7,000 이 더 취소됐다

        useCase.process(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(event.getLastError()).contains("외부 취소");
    }

    @Test
    @DisplayName("우리는 취소한 적이 없는데 토스가 전액 취소 상태면 외부 취소로 보고 실패로 남긴다")
    void externalFullCancelIsFailed() {
        tossSays("CANCELED", 0);

        useCase.process(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
    }

    @Test
    @DisplayName("모든 주문을 환불해 결제 건이 CANCELED 가 된 뒤의 전액 취소 웹훅은 이미 반영된 것으로 넘긴다")
    void fullCancelAfterAllRefundsIsIgnored() {
        charge.cancelPartially(33_000, NOW);
        charge.cancelPartially(17_000, NOW);
        tossSays("CANCELED", 0);

        useCase.process(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
    }
}
