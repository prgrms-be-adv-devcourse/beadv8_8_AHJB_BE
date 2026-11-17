package com.jjinmak.back.payment.in;

import com.jjinmak.back.payment.app.facade.PaymentFacade;
import com.jjinmak.back.payment.app.support.WalletRetrySupport;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문 모듈이 호출하는 PaymentPort 구현체.
 *
 * 지갑 낙관적 락 충돌은 결제 트랜잭션(PaymentFacade) 바깥인 여기서 재시도한다.
 * requestPayment 는 멱등하므로 통째로 다시 실행해도 안전하다.
 * 결제 승인은 포트가 아니라 결제 API(PaymentApiController)가 클라이언트에게서 직접 받는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentPortAdapter implements PaymentPort {

    private final PaymentFacade paymentFacade;
    private final WalletRetrySupport retry;

    @Override
    public PaymentResultDto requestPayment(PaymentRequestDto dto) {
        return retry.withRetry(() -> paymentFacade.requestPayment(dto));
    }
}
