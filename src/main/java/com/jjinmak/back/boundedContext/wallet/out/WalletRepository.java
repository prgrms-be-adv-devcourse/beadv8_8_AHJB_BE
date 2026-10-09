package com.jjinmak.back.boundedContext.wallet.out;

import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
}
