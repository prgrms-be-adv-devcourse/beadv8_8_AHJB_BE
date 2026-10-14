package com.jjinmak.back.boundedContext.wallet.app;

import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.INVALID_AMOUNT;
import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.WALLET_NOT_FOUND;

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
