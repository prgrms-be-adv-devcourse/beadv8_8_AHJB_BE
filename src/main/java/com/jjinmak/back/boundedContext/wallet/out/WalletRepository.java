package com.jjinmak.back.boundedContext.wallet.out;

import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByTypeAndHolderIsNull(WalletType type);

    Optional<Wallet> findByHolderId(Long holderId);

    Optional<Wallet> findByHolder(WalletMember member);

    boolean existsByTypeAndHolderIsNull(WalletType type);

    /**
     * 시스템 지갑(ESCROW/FEE)을 SELECT ... FOR UPDATE 로 조회한다.
     * 트랜잭션이 끝날 때까지 다른 트랜잭션은 이 row 를 기다린다.
     * 락 대기는 최대 3초. 넘기면 PessimisticLockingFailureException.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select w from Wallet w where w.type = :type and w.holder is null")
    Optional<Wallet> findSystemWalletForUpdate(@Param("type") WalletType type);
}
