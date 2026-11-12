package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.domain.WalletTransaction;
import com.jjinmak.payment.domain.enums.WalletAccount;
import com.jjinmak.payment.domain.enums.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<WalletTransaction> findByWallet(Wallet wallet, Pageable pageable);

    Page<WalletTransaction> findByWalletAndType(Wallet wallet, WalletTransactionType type, Pageable pageable);

    Page<WalletTransaction> findByWalletAndAccount(Wallet wallet, WalletAccount account, Pageable pageable);

    Page<WalletTransaction> findByWalletAndAccountAndType(Wallet wallet, WalletAccount account, WalletTransactionType type, Pageable pageable);
}
