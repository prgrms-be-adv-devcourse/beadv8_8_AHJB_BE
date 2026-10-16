package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** 지갑의 원장 (정렬은 Pageable 로) */
    Page<WalletTransaction> findByWallet(Wallet wallet, Pageable pageable);
}
