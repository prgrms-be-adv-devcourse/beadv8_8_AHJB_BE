package com.jjinmak.back.order.out.payment;

import com.jjinmak.back.shared.payment.port.PaymentPort;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRequest {

    private final PaymentPort paymentPort;

    public PaymentResultDto requestPayment(PaymentRequestDto dto){

        return paymentPort.requestPayment(dto);
    }

    public PaymentCloseResultDto closePayment(Long orderGroupId, Long buyerId){
        return paymentPort.closePayment(orderGroupId, buyerId);
    }
}
