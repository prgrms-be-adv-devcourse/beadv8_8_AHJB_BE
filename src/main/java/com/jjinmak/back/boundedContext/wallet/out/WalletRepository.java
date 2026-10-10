package com.jjinmak.back.boundedContext.wallet.out;

import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByTypeAndHolderIsNull(WalletType type);

    Optional<Wallet> findByHolderId(Long holderId);

    boolean existsByTypeAndHolderIsNull(WalletType type);
}
