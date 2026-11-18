package com.jjinmak.back.payment.out.repository;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChargeRepository extends JpaRepository<Charge, Long> {

    Optional<Charge> findByPgOrderId(String pgOrderId);

    Optional<Charge> findByPaymentKey(String paymentKey);

    // 같은 결제에 대해 아직 승인 전인 충전 요청이 있으면 재사용한다 (결제창을 닫고 다시 주문하기를 누른 경우)
    Optional<Charge> findFirstByPaymentAndStatusOrderByCreatedAtDesc(Payment payment, ChargeStatus status);

    List<Charge> findByPaymentOrderByIdDesc(Payment payment);

    Page<Charge> findByHolderIdAndPurpose(Long holderId, ChargePurpose purpose, Pageable pageable);

    /**
     * 선입선출 소비 대상: 이 지갑에 돈이 들어와 있고(CHARGED) 아직 남은 금액이 있는 충전을 오래된 순으로.
     * chargedAt 이 같으면 id 순. (DepositConsumer 전용)
     */
    List<Charge> findByWalletAndStatusAndRemainingAmountGreaterThanOrderByChargedAtAscIdAsc(
            Wallet wallet, ChargeStatus status, long remainingAmount);

    Optional<Charge> findByIdAndHolderId(Long id, Long holderId);

    // 복구 스케줄러용: 특정 상태에 너무 오래 머문 건
    List<Charge> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(ChargeStatus status, LocalDateTime before);

    // 복구 스케줄러용: 돈은 들어왔는데 홀딩이 안 된 주문 충전
    List<Charge> findTop100ByPurposeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            ChargePurpose purpose, ChargeStatus status, LocalDateTime before);

    // 대사용: 토스 거래에 나온 주문번호들의 충전 건
    List<Charge> findByPgOrderIdIn(Collection<String> pgOrderIds);

    // 대사용: 기간 안에 승인됐거나 취소된 충전 건 (startAt 이상, endAt 미만).
    // 토스 거래 목록에는 없는데 우리만 승인·취소로 알고 있는 건을 찾기 위해, 토스 쪽 주문번호와 별개로 조회한다
    @Query("select c from Charge c "
            + "where (c.approvedAt >= :startAt and c.approvedAt < :endAt) "
            + "or (c.canceledAt >= :startAt and c.canceledAt < :endAt)")
    List<Charge> findApprovedOrCanceledBetween(@Param("startAt") LocalDateTime startAt,
                                               @Param("endAt") LocalDateTime endAt);

    // 대사용 조회 결과: 미사용 충전 금액 합이 예치금보다 큰 지갑
    interface RemainingGap {
        Long getWalletId();

        Long getBalance();        // 지갑 예치금

        Long getRemainingSum();   // 그 지갑 충전 건들의 미사용 금액 합
    }

    // 대사용: 미사용 금액이 남은 충전 건을 가진 지갑 수
    @Query("select count(distinct c.wallet.id) from Charge c "
            + "where c.status = :status and c.remainingAmount > 0")
    long countWalletsWithRemaining(@Param("status") ChargeStatus status);

    // 대사용: 미사용 충전 금액 합이 예치금보다 큰 지갑. 예치금이 그 합 이상인 지갑은 돌려주지 않는다
    @Query("select w.id as walletId, w.balance as balance, sum(c.remainingAmount) as remainingSum "
            + "from Charge c join c.wallet w "
            + "where c.status = :status and c.remainingAmount > 0 "
            + "group by w.id, w.balance "
            + "having sum(c.remainingAmount) > w.balance")
    List<RemainingGap> findWalletsWithRemainingOverBalance(@Param("status") ChargeStatus status);

    // 대사용: 기간 안에 지갑에 반영됐거나(chargedAt) 취소된(canceledAt) 충전 건의 id (startAt 이상, endAt 미만)
    @Query("select c.id from Charge c "
            + "where (c.chargedAt >= :startAt and c.chargedAt < :endAt) "
            + "or (c.canceledAt >= :startAt and c.canceledAt < :endAt)")
    List<Long> findIdsChargedOrCanceledBetween(@Param("startAt") LocalDateTime startAt,
                                               @Param("endAt") LocalDateTime endAt);
}
