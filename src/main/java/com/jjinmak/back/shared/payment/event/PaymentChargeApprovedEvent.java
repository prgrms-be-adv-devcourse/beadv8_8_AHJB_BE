package com.jjinmak.back.shared.payment.event;

import com.jjinmak.back.shared.payment.dto.ChargeDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentChargeApprovedEvent {
    ChargeDto chargeDto;
}
