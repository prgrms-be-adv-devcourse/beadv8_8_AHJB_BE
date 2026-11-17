package com.jjinmak.back.payment.out.repository;

import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.ReferenceType;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<WalletTransaction> findByWallet(Wallet wallet, Pageable pageable);

    Page<WalletTransaction> findByWalletAndType(Wallet wallet, WalletTransactionType type, Pageable pageable);

    Page<WalletTransaction> findByWalletAndAccount(Wallet wallet, WalletAccount account, Pageable pageable);

    Page<WalletTransaction> findByWalletAndAccountAndType(Wallet wallet, WalletAccount account, WalletTransactionType type, Pageable pageable);

    // 대사용 조회 결과: 거래 그룹과 그 그룹의 금액 합
    interface GroupSum {
        String getGroupId();

        Long getTotal();
    }

    // 대사용: 기간 안에 기록이 생긴 거래 그룹 수 (startAt 이상, endAt 미만). excludedTypes 유형의 기록은 세지 않는다
    @Query("select count(distinct t.transactionGroupId) from WalletTransaction t "
            + "where t.type not in :excludedTypes "
            + "and t.createdAt >= :startAt and t.createdAt < :endAt")
    long countGroupsCreatedBetween(@Param("excludedTypes") Collection<WalletTransactionType> excludedTypes,
                                   @Param("startAt") LocalDateTime startAt,
                                   @Param("endAt") LocalDateTime endAt);

    /**
     * 대사용: 기간 안에 기록이 생긴 거래 그룹 중 금액 합이 0 이 아닌 그룹.
     * 그룹을 고를 때만 기간을 쓰고, 합계는 그 그룹의 전체 기록으로 낸다 (자정에 걸친 그룹이 반쪽만 더해지지 않게).
     */
    @Query("select t.transactionGroupId as groupId, sum(t.amount) as total from WalletTransaction t "
            + "where t.type not in :excludedTypes "
            + "and t.transactionGroupId in ("
            + "    select t2.transactionGroupId from WalletTransaction t2 "
            + "    where t2.type not in :excludedTypes "
            + "    and t2.createdAt >= :startAt and t2.createdAt < :endAt) "
            + "group by t.transactionGroupId "
            + "having sum(t.amount) <> 0")
    List<GroupSum> findUnbalancedGroupsCreatedBetween(@Param("excludedTypes") Collection<WalletTransactionType> excludedTypes,
                                                      @Param("startAt") LocalDateTime startAt,
                                                      @Param("endAt") LocalDateTime endAt);

    // 대사용: 기간 안에 생긴 기록이 가리키는 대상의 id (충전 건 id 또는 주문 id). 대상 종류와 거래 유형으로 한정한다
    @Query("select distinct t.referenceId from WalletTransaction t "
            + "where t.referenceType = :referenceType and t.type in :types "
            + "and t.createdAt >= :startAt and t.createdAt < :endAt")
    List<Long> findReferenceIdsCreatedBetween(@Param("referenceType") ReferenceType referenceType,
                                              @Param("types") Collection<WalletTransactionType> types,
                                              @Param("startAt") LocalDateTime startAt,
                                              @Param("endAt") LocalDateTime endAt);

    // 대사용: 대상들의 원장 기록 전체 (기간과 무관)
    List<WalletTransaction> findByReferenceTypeAndTypeInAndReferenceIdIn(ReferenceType referenceType,
                                                                         Collection<WalletTransactionType> types,
                                                                         Collection<Long> referenceIds);
}
