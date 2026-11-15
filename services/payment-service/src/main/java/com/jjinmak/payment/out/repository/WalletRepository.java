package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.domain.WalletMember;
import com.jjinmak.payment.domain.enums.WalletType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    // 대사용 조회 결과: 잔액이 원장 합계와 다른 지갑의 한 주머니
    interface BalanceGap {
        Long getWalletId();

        Long getBalance();     // 지갑에 적힌 그 주머니의 잔액

        Long getLedgerSum();   // 그 주머니 원장 기록의 금액 합
    }

    /**
     * 대사용: 예치금(balance)이 예치금 원장 합계와 다른 지갑.
     * 원장 기록이 하나도 없는 지갑은 합계를 0 으로 본다. 잔액과 합계가 같은 지갑은 돌려주지 않는다.
     */
    @Query("select w.id as walletId, w.balance as balance, coalesce(sum(t.amount), 0) as ledgerSum "
            + "from Wallet w "
            + "left join WalletTransaction t on t.wallet.id = w.id "
            + "and t.account = com.jjinmak.payment.domain.enums.WalletAccount.DEPOSIT "
            + "group by w.id, w.balance "
            + "having w.balance <> coalesce(sum(t.amount), 0)")
    List<BalanceGap> findDepositLedgerGaps();

    // 대사용: 정산금(settlementBalance)이 정산금 원장 합계와 다른 지갑. 조건은 위와 같다
    @Query("select w.id as walletId, w.settlementBalance as balance, coalesce(sum(t.amount), 0) as ledgerSum "
            + "from Wallet w "
            + "left join WalletTransaction t on t.wallet.id = w.id "
            + "and t.account = com.jjinmak.payment.domain.enums.WalletAccount.SETTLEMENT "
            + "group by w.id, w.settlementBalance "
            + "having w.settlementBalance <> coalesce(sum(t.amount), 0)")
    List<BalanceGap> findSettlementLedgerGaps();
}
