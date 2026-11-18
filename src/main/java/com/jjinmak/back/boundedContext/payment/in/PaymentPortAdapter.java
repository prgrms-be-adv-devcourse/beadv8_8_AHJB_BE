package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.app.PaymentFacade;
import com.jjinmak.back.boundedContext.payment.app.WalletFacade;
import com.jjinmak.back.boundedContext.payment.app.WalletRetrySupport;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.shared.payment.dto.HoldingSettlementDto;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문 컨텍스트가 호출하는 PaymentPort 구현체. 나중에 결제가 별도 서비스로 분리되면 이 자리에 REST 클라이언트 구현체가 들어간다.
 * 지갑 낙관적 락 충돌은 트랜잭션 바깥인 여기서 재시도한다. (requestPayment 는 멱등하므로 통째로 다시 실행해도 안전)
 * 결제 승인은 포트가 아니라 결제 API(PaymentApiController)가 클라이언트에게서 직접 받는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentPortAdapter implements PaymentPort {

    private final PaymentFacade paymentFacade;
    private final WalletFacade walletFacade;
    private final WalletRetrySupport retry;

    @Override
    public PaymentResultDto requestPayment(PaymentRequestDto dto) {
        return retry.withRetry(() -> paymentFacade.requestPayment(dto));
    }

    @Override
    public long getBalance(Long holderId) {
        return walletFacade.getBalance(holderId);
    }

    @Override
    public HoldingSettlementDto findSettlementOf(Long orderId) {
        return walletFacade.getSettlement(orderId);
    }
}
