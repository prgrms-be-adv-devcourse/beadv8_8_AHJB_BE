package com.jjinmak.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.domain.WalletMember;
import com.jjinmak.payment.out.repository.WalletMemberRepository;
import com.jjinmak.payment.out.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.jjinmak.payment.exception.WalletErrorCode.WALLET_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WalletSupport {
    private final WalletMemberRepository walletMemberRepository;
    private final WalletRepository walletRepository;

    public Optional<WalletMember> findMemberById(Long id) {
        return walletMemberRepository.findById(id);
    }

    public Optional<Wallet> findWalletByHolder(WalletMember holder) {
        return walletRepository.findByHolder(holder);
    }

    public Optional<Wallet> findWalletByHolderId(Long holderId) {
        return walletRepository.findByHolderId(holderId);
    }

    /** 없으면 WALLET_NOT_FOUND */
    public Wallet getWalletByHolderId(Long holderId) {
        return findWalletByHolderId(holderId)
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND));
    }
}
