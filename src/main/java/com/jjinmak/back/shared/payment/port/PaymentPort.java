package com.jjinmak.back.shared.payment.port;

import com.jjinmak.back.shared.payment.dto.PaymentConfirmDto;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;

/**
 * 주문 컨텍스트가 결제 컨텍스트를 동기 호출할 때 쓰는 포트. 구현체는 결제 컨텍스트의 in/PaymentPortAdapter.
 *
 * 흐름
 *  1. 주문하기            → requestPayment : 예치금이 충분하면 바로 PAID, 부족하면 NEEDS_CHARGE(pgOrderId, 부족분)
 *  2. (부족분만) PG 결제창 → 클라이언트가 토스 결제창에서 부족분을 결제하고 successUrl 로 복귀
 *  3. 승인                → confirmPayment : 토스 승인 + 충전 + 차감 + 홀딩을 끝내고 PAID
 *
 * 두 메서드 모두 멱등하다. 같은 orderGroupId 로 다시 호출하면 이미 끝난 결제는 PAID 를 그대로 돌려준다.
 *
 * 주의: confirmPayment 는 내부에서 토스 HTTP 호출을 하므로, 호출하는 쪽의 @Transactional 안에서 부르지 말 것.
 *       (주문 상태 갱신은 이 메서드가 반환된 뒤 별도 트랜잭션에서)
 */
public interface PaymentPort {

    PaymentResultDto requestPayment(PaymentRequestDto dto);

    PaymentResultDto confirmPayment(PaymentConfirmDto dto);

    long getBalance(Long holderId);
}
