package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.ReferenceType;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LedgerRecorder {

    private final WalletTransactionRepository walletTransactionRepository;

    // 예치금 -> 예치금으로 amount 를 옮기고 원장 한 쌍을 남긴다. 잔액 변경과 기록이 분리될 수 없게 한다.
    public void transfer(Wallet from, WalletTransactionType fromType,
                         Wallet to, WalletTransactionType toType,
                         long amount, String groupId, ReferenceType refType, Long refId) {
        from.withdrawal(amount);
        to.deposit(amount);
        walletTransactionRepository.save(WalletTransaction.of(from, fromType, -amount, groupId, refType, refId));
        walletTransactionRepository.save(WalletTransaction.of(to,   toType,    amount, groupId, refType, refId));
    }


    // 예치금 -> 정산금으로 amount 를 옮긴다. 구매 확정 릴리즈(ESCROW -> 판매자 정산금) 전용.
    // 나가는 쪽은 DEPOSIT, 들어오는 쪽은 SETTLEMENT 주머니로 기록된다.
    public void transferToSettlement(Wallet from, WalletTransactionType fromType,
                                     Wallet to, WalletTransactionType toType,
                                     long amount, String groupId, ReferenceType refType, Long refId) {
        from.withdrawal(amount);
        to.depositSettlement(amount);
        walletTransactionRepository.save(WalletTransaction.of(from, WalletAccount.DEPOSIT,    fromType, -amount, groupId, refType, refId));
        walletTransactionRepository.save(WalletTransaction.of(to,   WalletAccount.SETTLEMENT, toType,    amount, groupId, refType, refId));
    }


    // 포인트 -> 예치금으로 amount 를 옮긴다. 포인트 결제(구매자 포인트 -> ESCROW) 전용.
    // 나가는 쪽은 POINT, 들어오는 쪽은 DEPOSIT 주머니로 기록된다. ESCROW 는 포인트로 받은 금액도 예치금으로 보관한다.
    public void transferPointToDeposit(Wallet from, WalletTransactionType fromType,
                                       Wallet to, WalletTransactionType toType,
                                       long amount, String groupId, ReferenceType refType, Long refId) {
        from.usePoint(amount);
        to.deposit(amount);
        walletTransactionRepository.save(WalletTransaction.of(from, WalletAccount.POINT,   fromType, -amount, groupId, refType, refId));
        walletTransactionRepository.save(WalletTransaction.of(to,   WalletAccount.DEPOSIT, toType,    amount, groupId, refType, refId));
    }

    // 예치금 -> 포인트로 amount 를 옮긴다. 포인트 환불(ESCROW -> 구매자 포인트) 전용.
    // 나가는 쪽은 DEPOSIT, 들어오는 쪽은 POINT 주머니로 기록된다.
    public void transferDepositToPoint(Wallet from, WalletTransactionType fromType,
                                       Wallet to, WalletTransactionType toType,
                                       long amount, String groupId, ReferenceType refType, Long refId) {
        from.withdrawal(amount);
        to.depositPoint(amount);
        walletTransactionRepository.save(WalletTransaction.of(from, WalletAccount.DEPOSIT, fromType, -amount, groupId, refType, refId));
        walletTransactionRepository.save(WalletTransaction.of(to,   WalletAccount.POINT,   toType,    amount, groupId, refType, refId));
    }

    // 플랫폼이 지급하는 포인트 (적립). 상대 계정이 원장 밖이라 한 줄
    public void depositPoint(Wallet wallet, WalletTransactionType type, long amount, ReferenceType refType, Long refId) {
        wallet.depositPoint(amount);
        walletTransactionRepository.save(WalletTransaction.of(
                wallet, WalletAccount.POINT, type, amount, UUID.randomUUID().toString(), refType, refId));
    }

    // 외부(PG)에서 들어오거나 나가는 돈. 상대 계정이 원장 밖이라 한 줄
    public void deposit(Wallet wallet, WalletTransactionType type, long amount, ReferenceType refType, Long refId) {
        wallet.deposit(amount);
        walletTransactionRepository.save(WalletTransaction.of(wallet, type, amount, UUID.randomUUID().toString(), refType, refId));
    }

    public void withdraw(Wallet wallet, WalletTransactionType type, long amount, ReferenceType refType, Long refId) {
        wallet.withdrawal(amount);
        walletTransactionRepository.save(WalletTransaction.of(wallet, type, -amount, UUID.randomUUID().toString(), refType, refId));
    }

    // 정산금이 외부(계좌)로 나가는 돈. 상대 계정이 원장 밖이라 한 줄. SETTLEMENT 주머니로 기록된다
    public void withdrawSettlement(Wallet wallet, WalletTransactionType type, long amount, ReferenceType refType, Long refId) {
        wallet.withdrawSettlement(amount);
        walletTransactionRepository.save(WalletTransaction.of(
                wallet, WalletAccount.SETTLEMENT, type, -amount, UUID.randomUUID().toString(), refType, refId));
    }
}
