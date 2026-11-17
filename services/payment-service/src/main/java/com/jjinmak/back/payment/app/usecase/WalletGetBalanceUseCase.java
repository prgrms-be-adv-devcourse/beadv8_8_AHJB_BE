package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.app.dto.WalletBalanceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.payment.exception.WalletErrorCode.WALLET_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WalletGetBalanceUseCase {
    private final WalletSupport walletSupport;

    public long getBalance(Long holderId) {
        return walletSupport.findWalletByHolderId(holderId)
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND))
                .getBalance();
    }

    public WalletBalanceDto getBalances(Long holderId) {
        return walletSupport.findWalletByHolderId(holderId)
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND))
                .toDto();
    }
}
