package com.jjinmak.back.boundedContext.wallet.in;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.dto.HoldDto;
import com.jjinmak.back.shared.wallet.port.WalletPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletPortAdapter implements WalletPort {

    private final WalletFacade walletFacade;

    @Override
    public void charge(ChargeDto dto) {
        walletFacade.charge(dto);
    }

    @Override
    public void cancelCharge(ChargeDto dto) {
        walletFacade.cancelCharge(dto);
    }

    @Override
    public void revertCancelCharge(ChargeDto dto) {
        walletFacade.revertCancelCharge(dto);
    }

    @Override
    public long getBalance(Long holderId) {
        return walletFacade.getBalance(holderId);
    }

    @Override
    public void hold(HoldDto dto) {
        walletFacade.hold(dto);
    }
}
