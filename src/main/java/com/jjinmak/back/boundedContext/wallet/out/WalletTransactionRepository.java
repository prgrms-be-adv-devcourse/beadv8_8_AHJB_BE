package com.jjinmak.back.boundedContext.wallet.out;

import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
}
