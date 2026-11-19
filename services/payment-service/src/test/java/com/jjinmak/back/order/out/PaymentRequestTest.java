package com.jjinmak.back.order.out;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.back.order.out.payment.PaymentRequest;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static com.jjinmak.back.payment.exception.WalletErrorCode.WALLET_CONCURRENT_MODIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRequestTest {

    PaymentPort paymentPort;

    PaymentRequest paymentRequest;

    PaymentRequestDto request = new PaymentRequestDto(50L, 1L, 33_000L,
            List.of(new PaymentRequestDto.Item(100L, 2L, 30_000L, 3_000L)));

    @BeforeEach
    void setUp() {
        paymentPort = mock(PaymentPort.class);
        paymentRequest = new PaymentRequest(paymentPort);
    }

    @Test
    @DisplayName("결제 요청 정보를 그대로 결제 포트에 넘기고, 결제 포트가 돌려준 결제 결과(NEEDS_CHARGE)를 그대로 돌려준다")
    void requestPayment_needsCharge() {
        PaymentResultDto needsCharge = PaymentResultDto.needsCharge(7L, 33_000L, 13_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)");
        when(paymentPort.requestPayment(request)).thenReturn(needsCharge);

        PaymentResultDto result = paymentRequest.requestPayment(request);

        assertThat(result).isEqualTo(needsCharge);
        verify(paymentPort).requestPayment(request);
    }

    @Test
    @DisplayName("결제 포트가 예치금으로 결제했다고(PAID) 돌려주면 PAID 결과를 돌려준다")
    void requestPayment_paid() {
        PaymentResultDto paid = PaymentResultDto.paid(7L, 33_000L);
        when(paymentPort.requestPayment(request)).thenReturn(paid);

        PaymentResultDto result = paymentRequest.requestPayment(request);

        assertThat(result).isEqualTo(paid);
        assertThat(result.isPaid()).isTrue();
    }

    @Test
    @DisplayName("결제 포트가 던진 결제 쪽 예외는 다른 에러 코드로 바꾸지 않고 그대로 올린다")
    void requestPayment_paymentErrorPropagates() {
        BusinessException paymentError = new BusinessException(WALLET_CONCURRENT_MODIFICATION);
        when(paymentPort.requestPayment(request)).thenThrow(paymentError);

        assertThatThrownBy(() -> paymentRequest.requestPayment(request))
                .isSameAs(paymentError);
    }

    @Test
    @DisplayName("결제 닫기는 주문 그룹 id와 구매자 id를 그 순서로 결제 포트에 넘기고, 결제 포트가 돌려준 결과를 그대로 돌려준다")
    void closePayment_passesGroupAndBuyer() {
        PaymentCloseResultDto closed = PaymentCloseResultDto.closed(50L, 7L);
        when(paymentPort.closePayment(50L, 1L)).thenReturn(closed);

        PaymentCloseResultDto result = paymentRequest.closePayment(50L, 1L);

        assertThat(result).isSameAs(closed);
        verify(paymentPort).closePayment(50L, 1L);
    }

    @Test
    @DisplayName("결제 닫기에서 결제 포트가 던진 결제 쪽 예외는 다른 에러 코드로 바꾸지 않고 그대로 올린다")
    void closePayment_paymentErrorPropagates() {
        BusinessException forbidden = new BusinessException(PAYMENT_FORBIDDEN);
        when(paymentPort.closePayment(50L, 1L)).thenThrow(forbidden);

        assertThatThrownBy(() -> paymentRequest.closePayment(50L, 1L))
                .isSameAs(forbidden);
    }
}
