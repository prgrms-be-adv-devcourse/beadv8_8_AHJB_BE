package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.LedgerEntryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.LedgerPageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 내 예치금 원장 조회. 최신순, 한 페이지 최대 50건.
 * type 을 주면 그 유형만 (예: SETTLEMENT_RECEIVE 로 정산 수령 내역, REFUND_RECEIVE 로 환불 수령 내역).
 * 원장은 append-only 라 수정·삭제 없이 쌓인 그대로를 보여준다.
 */
@Service
@RequiredArgsConstructor
public class WalletGetLedgerUseCase {

    private final WalletSupport walletSupport;
    private final WalletTransactionRepository walletTransactionRepository;

    public LedgerPageDto get(Long holderId, WalletTransactionType type, int page, int size) {
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);
        PageRequest pageRequest = PageDto.latest(page, size);

        Page<LedgerEntryDto> entries = (type == null
                ? walletTransactionRepository.findByWallet(wallet, pageRequest)
                : walletTransactionRepository.findByWalletAndType(wallet, type, pageRequest))
                .map(LedgerEntryDto::from);

        return LedgerPageDto.of(wallet.getBalance(), entries);
    }
}
