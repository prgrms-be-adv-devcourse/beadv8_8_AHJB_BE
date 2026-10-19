package com.jjinmak.back.shared.payment.port;

import com.jjinmak.back.shared.payment.dto.HoldingSettlementDto;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;

/**
 * 주문 컨텍스트가 결제 컨텍스트를 동기 호출할 때 쓰는 포트. 구현체는 결제 컨텍스트의 in/PaymentPortAdapter.
 *
 * 흐름
 *  1. 주문하기            → requestPayment : 예치금이 충분하면 바로 PAID, 부족하면 NEEDS_CHARGE(pgOrderId, 부족분, orderName)
 *                           주문은 반환값을 해석하지 않고 그대로 클라이언트에 내려준다 (결제창 파라미터의 주인은 결제)
 *  2. (부족분만) PG 결제창 → 클라이언트가 토스 결제창에서 부족분을 결제하고 successUrl 로 복귀
 *  3. 승인                → 클라이언트가 결제 API(POST /api/v1/payments/confirm)를 직접 호출. 주문은 관여하지 않는다
 *  4. 결제 완료 통지       → 결제가 PaymentPaidEvent 를 발행. 주문은 AFTER_COMMIT 리스너로 받아 그룹을 결제 완료로 바꾼다
 *                           (전액 예치금으로 1 에서 바로 PAID 된 경우에도 같은 이벤트가 발행된다)
 *
 * requestPayment 는 멱등하다. 같은 orderGroupId 로 다시 호출하면 이미 끝난 결제는 PAID 를 그대로 돌려준다.
 */
public interface PaymentPort {

    PaymentResultDto requestPayment(PaymentRequestDto dto);

    long getBalance(Long holderId);

    HoldingSettlementDto findSettlementOf(Long orderId);
}
