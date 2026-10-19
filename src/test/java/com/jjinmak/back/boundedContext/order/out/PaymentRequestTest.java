package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.boundedContext.order.out.payment.PaymentRequest;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRequestTest {

    @Mock
    PaymentPort paymentPort;

    @InjectMocks
    PaymentRequest paymentRequest;

    @Test
    @DisplayName("결제 요청 정보를 결제 포트에 그대로 넘기고, 포트의 결과를 그대로 돌려준다")
    void requestPayment_delegatesToPaymentPort() {
        PaymentRequestDto request = new PaymentRequestDto(50L, 1L, 33_000L,
                List.of(new PaymentRequestDto.Item(100L, 2L, 30_000L, 3_000L)));
        PaymentResultDto result = PaymentResultDto.needsCharge(7L, 33_000L, 13_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)");
        when(paymentPort.requestPayment(request)).thenReturn(result);

        PaymentResultDto returned = paymentRequest.requestPayment(request);

        assertThat(returned).isSameAs(result);
        verify(paymentPort).requestPayment(request);
    }
}
