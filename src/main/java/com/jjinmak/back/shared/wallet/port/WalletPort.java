package com.jjinmak.back.shared.wallet.port;

import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.dto.HoldDto;

public interface WalletPort {

    void charge(ChargeDto dto);

    void cancelCharge(ChargeDto dto);

    void revertCancelCharge(ChargeDto dto);

    void hold(HoldDto dto);

    long getBalance(Long holderId);
}


