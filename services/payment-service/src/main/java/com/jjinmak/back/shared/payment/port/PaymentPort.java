package com.jjinmak.back.shared.payment.port;

import com.jjinmak.contracts.payment.dto.HoldingSettlementDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;

public interface PaymentPort {

    PaymentResultDto requestPayment(PaymentRequestDto dto);

    HoldingSettlementDto findSettlementOf(Long orderId);
}
