package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.app.dto.LedgerEntryDto;
import com.jjinmak.back.payment.app.dto.LedgerPageDto;
import com.jjinmak.back.payment.app.dto.PageDto;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class WalletGetLedgerUseCase {

    private final WalletSupport walletSupport;
    private final WalletTransactionRepository walletTransactionRepository;

    public LedgerPageDto get(Long holderId, WalletAccount account, WalletTransactionType type, int page, int size) {
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);
        PageRequest pageRequest = PageDto.latest(page, size);

        Page<LedgerEntryDto> entries = find(wallet, account, type, pageRequest).map(LedgerEntryDto::from);

        return LedgerPageDto.of(wallet.getBalance(), wallet.getSettlementBalance(), wallet.getPointBalance(), entries);
    }

    private Page<WalletTransaction> find(Wallet wallet, WalletAccount account, WalletTransactionType type, PageRequest pageRequest) {
        if (account == null && type == null) return walletTransactionRepository.findByWallet(wallet, pageRequest);
        if (account == null)                 return walletTransactionRepository.findByWalletAndType(wallet, type, pageRequest);
        if (type == null)                    return walletTransactionRepository.findByWalletAndAccount(wallet, account, pageRequest);
        return walletTransactionRepository.findByWalletAndAccountAndType(wallet, account, type, pageRequest);
    }
}
