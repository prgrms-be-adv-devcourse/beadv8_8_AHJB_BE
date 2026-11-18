package com.jjinmak.back.boundedContext.order.out.payment;

import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRequest {

    private final PaymentPort paymentPort;

    public PaymentResultDto requestPayment(PaymentRequestDto dto){
        return paymentPort.requestPayment(dto);
    }
}
