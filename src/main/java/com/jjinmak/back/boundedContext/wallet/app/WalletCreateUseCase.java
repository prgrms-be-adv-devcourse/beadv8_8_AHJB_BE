package com.jjinmak.back.boundedContext.wallet.app;


import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.shared.wallet.dto.WalletMemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletCreateUseCase {
    private final WalletRepository walletRepository;
    private final WalletMemberRepository walletMemberRepository;

    public void createWallet(WalletMemberDto member) {
        WalletMember _member = walletMemberRepository.getReferenceById(member.id());
        Wallet wallet = Wallet.createUserWallet(_member);

        walletRepository.save(wallet);
    }
}
