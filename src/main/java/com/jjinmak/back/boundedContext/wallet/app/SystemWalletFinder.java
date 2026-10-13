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

    /** 잔액을 변겨할 때, 호출하는 트랜잭션이 끝날 때까지 락을 잡음 */
    public Wallet escrowForUpdate() {
        return findForUpdate(WalletType.ESCROW);
    }

    public Wallet feeForUpdate() {
        return findForUpdate(WalletType.FEE);
    }

    /** 읽기만 할 때 사용 */
    public Wallet escrow() {
        return find(WalletType.ESCROW);
    }

    public Wallet fee() {
        return find(WalletType.FEE);
    }


    private Wallet findForUpdate(WalletType type) {
        return walletRepository.findSystemWalletForUpdate(type)
                .orElseThrow(() -> new BusinessException(SYSTEM_WALLET_NOT_FOUND));
    }

    private Wallet find(WalletType type) {
        return walletRepository.findByTypeAndHolderIsNull(type)
                .orElseThrow(() -> new BusinessException(SYSTEM_WALLET_NOT_FOUND));
    }
}
