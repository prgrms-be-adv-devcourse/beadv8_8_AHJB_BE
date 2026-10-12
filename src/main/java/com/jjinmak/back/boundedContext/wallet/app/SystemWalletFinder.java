package com.jjinmak.back.boundedContext.wallet.app;

import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.SYSTEM_WALLET_NOT_FOUND;

@Component
@RequiredArgsConstructor
public class SystemWalletFinder {

    private final WalletRepository walletRepository;

    public Wallet escrow() {
        return find(WalletType.ESCROW);
    }

    public Wallet fee() {
        return find(WalletType.FEE);
    }

    private Wallet find(WalletType type) {
        return walletRepository.findByTypeAndHolderIsNull(type)
                .orElseThrow(() -> new BusinessException(SYSTEM_WALLET_NOT_FOUND));
    }
}
