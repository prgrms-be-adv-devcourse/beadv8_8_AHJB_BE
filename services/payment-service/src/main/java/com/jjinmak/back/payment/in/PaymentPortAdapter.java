package com.jjinmak.back.payment.in;

import com.jjinmak.back.payment.app.facade.PaymentFacade;
import com.jjinmak.back.payment.app.facade.WalletFacade;
import com.jjinmak.back.payment.app.support.WalletRetrySupport;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import com.jjinmak.contracts.payment.dto.HoldingSettlementDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


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
    public HoldingSettlementDto findSettlementOf(Long orderId) {
        return walletFacade.getSettlement(orderId);
    }
}
