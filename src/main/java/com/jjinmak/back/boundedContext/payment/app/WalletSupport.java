package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
}
