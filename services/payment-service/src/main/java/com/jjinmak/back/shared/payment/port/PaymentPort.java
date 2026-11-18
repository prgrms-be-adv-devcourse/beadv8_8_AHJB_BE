package com.jjinmak.back.shared.payment.port;

import com.jjinmak.contracts.payment.dto.HoldingSettlementDto;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;

public interface PaymentPort {

    PaymentResultDto requestPayment(PaymentRequestDto dto);

    HoldingSettlementDto findSettlementOf(Long orderId);

    /**
     * 주문이 주문 그룹을 버릴 때(결제창 닫힘 등) 그 그룹의 결제를 닫는다. 닫힌 뒤에는 이 orderGroupId 로 승인도 결제 요청도 되지 않는다.
     * 멱등하다. 이미 닫힌 결제는 CLOSED, 이미 끝난 결제는 닫지 않고 ALREADY_PAID 를 돌려준다.
     * buyerId 가 결제의 구매자와 다르면 PAYMENT_FORBIDDEN, 승인이 진행 중이라 기다리다 시간이 넘으면 PAYMENT_IN_PROGRESS 예외를 던진다.
     */
    PaymentCloseResultDto closePayment(Long orderGroupId, Long buyerId);
}
