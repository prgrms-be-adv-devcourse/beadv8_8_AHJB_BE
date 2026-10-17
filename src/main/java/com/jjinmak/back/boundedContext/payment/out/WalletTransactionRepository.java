package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletAccount;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** 지갑의 원장 (정렬은 Pageable 로) */
    Page<WalletTransaction> findByWallet(Wallet wallet, Pageable pageable);

    /** 지갑의 원장 중 특정 유형만 (정산 수령, 환불 수령 등) */
    Page<WalletTransaction> findByWalletAndType(Wallet wallet, WalletTransactionType type, Pageable pageable);

    /** 지갑의 원장 중 특정 주머니만 (DEPOSIT = 예치금, SETTLEMENT = 정산금) */
    Page<WalletTransaction> findByWalletAndAccount(Wallet wallet, WalletAccount account, Pageable pageable);

    Page<WalletTransaction> findByWalletAndAccountAndType(Wallet wallet, WalletAccount account, WalletTransactionType type, Pageable pageable);
}
