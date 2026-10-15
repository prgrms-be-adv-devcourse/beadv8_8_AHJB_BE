package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.WALLET_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WalletGetBalanceUseCase {
    private final WalletSupport walletSupport;

    public long getBalance(Long holderId) {
        return walletSupport.findWalletByHolderId(holderId)
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND))
                .getBalance();
    }
}
