package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletAccount;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LedgerRecorder {

    private final WalletTransactionRepository walletTransactionRepository;

    /** from 예치금 → to 예치금으로 amount 를 옮기고 원장 한 쌍을 남긴다. 잔액 변경과 기록이 분리될 수 없게 한다. */
    public void transfer(Wallet from, WalletTransactionType fromType,
                         Wallet to, WalletTransactionType toType,
                         long amount, String groupId, ReferenceType refType, Long refId) {
        from.withdrawal(amount);
        to.deposit(amount);
        walletTransactionRepository.save(WalletTransaction.of(from, fromType, -amount, groupId, refType, refId));
        walletTransactionRepository.save(WalletTransaction.of(to,   toType,    amount, groupId, refType, refId));
    }

    /**
     * from 예치금 → to 정산금으로 amount 를 옮긴다. 구매 확정 릴리즈(ESCROW → 판매자 정산금) 전용.
     * 나가는 쪽은 DEPOSIT, 들어오는 쪽은 SETTLEMENT 주머니로 기록된다.
     */
    public void transferToSettlement(Wallet from, WalletTransactionType fromType,
                                     Wallet to, WalletTransactionType toType,
                                     long amount, String groupId, ReferenceType refType, Long refId) {
        from.withdrawal(amount);
        to.depositSettlement(amount);
        walletTransactionRepository.save(WalletTransaction.of(from, WalletAccount.DEPOSIT,    fromType, -amount, groupId, refType, refId));
        walletTransactionRepository.save(WalletTransaction.of(to,   WalletAccount.SETTLEMENT, toType,    amount, groupId, refType, refId));
    }

    /** 외부(PG)에서 들어오거나 나가는 돈. 상대 계정이 원장 밖이라 한 줄 */
    public void deposit(Wallet wallet, WalletTransactionType type, long amount, ReferenceType refType, Long refId) {
        wallet.deposit(amount);
        walletTransactionRepository.save(WalletTransaction.of(wallet, type, amount, UUID.randomUUID().toString(), refType, refId));
    }

    public void withdraw(Wallet wallet, WalletTransactionType type, long amount, ReferenceType refType, Long refId) {
        wallet.withdrawal(amount);
        walletTransactionRepository.save(WalletTransaction.of(wallet, type, -amount, UUID.randomUUID().toString(), refType, refId));
    }
}