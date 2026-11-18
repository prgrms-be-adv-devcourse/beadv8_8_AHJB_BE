package com.jjinmak.back.boundedContext.payment.app;


import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.shared.payment.dto.WalletMemberDto;
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
