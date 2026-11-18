package com.jjinmak.back.payment.app.usecase;


import com.jjinmak.contracts.payment.dto.WalletMemberDto;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
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
