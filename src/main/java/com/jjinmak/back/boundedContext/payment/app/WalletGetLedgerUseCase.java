package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.LedgerEntryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.LedgerPageDto;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 내 예치금 원장 조회. 최신순, 한 페이지 최대 50건.
 * 원장은 append-only 라 수정·삭제 없이 쌓인 그대로를 보여준다.
 */
@Service
@RequiredArgsConstructor
public class WalletGetLedgerUseCase {

    private static final int MAX_SIZE = 50;

    private final WalletSupport walletSupport;
    private final WalletTransactionRepository walletTransactionRepository;

    public LedgerPageDto get(Long holderId, int page, int size) {
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));

        Page<LedgerEntryDto> entries = walletTransactionRepository
                .findByWallet(wallet, pageRequest)
                .map(LedgerEntryDto::from);

        return LedgerPageDto.of(wallet.getBalance(), entries);
    }
}
