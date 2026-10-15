package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.boundedContext.payment.app.dto.ChargeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.INVALID_AMOUNT;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.WALLET_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WalletChargeUseCase {

    private final WalletSupport walletSupport;
    private final LedgerRecorder ledgerRecorder;

    public void charge(ChargeDto chargeDto) {
        Wallet customerWallet = walletSupport.findWalletByHolderId(chargeDto.holderId())
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND));

        if (chargeDto.amount() <= 0) {
            throw new BusinessException(INVALID_AMOUNT);
        }

        ledgerRecorder.deposit(customerWallet, CHARGE, chargeDto.amount(), PAYMENT, chargeDto.paymentId());
    }
}
