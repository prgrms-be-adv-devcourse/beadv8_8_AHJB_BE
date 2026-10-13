package com.jjinmak.back.shared.wallet.port;

import com.jjinmak.back.shared.payment.dto.ChargeDto;

public interface WalletChargeCancelPort {

    void cancelCharge(ChargeDto dto);

    void revertCancelCharge(ChargeDto dto);
}


