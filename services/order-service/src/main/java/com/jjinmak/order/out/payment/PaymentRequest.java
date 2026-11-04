package com.jjinmak.order.out.payment;

import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.contracts.payment.port.PaymentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRequest {

//    private final PaymentPort paymentPort;
//
//    public PaymentResultDto requestPayment(PaymentRequestDto dto){
//        return paymentPort.requestPayment(dto);
//    }
}
