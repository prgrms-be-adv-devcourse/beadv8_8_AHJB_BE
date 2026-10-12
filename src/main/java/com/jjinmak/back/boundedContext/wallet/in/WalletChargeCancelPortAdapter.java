package com.jjinmak.back.boundedContext.wallet.in;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.port.WalletChargeCancelPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletChargeCancelPortAdapter implements WalletChargeCancelPort {

    private final WalletFacade walletFacade;

    @Override
    public void cancelCharge(ChargeDto dto) {
        walletFacade.cancelCharge(dto);
    }

    @Override
    public void revertCancelCharge(ChargeDto dto) {
        walletFacade.revertCancelCharge(dto);
    }
}
