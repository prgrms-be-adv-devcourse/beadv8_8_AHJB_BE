package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.Payment;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.domain.enums.ChargePurpose;
import com.jjinmak.payment.domain.enums.ChargeStatus;
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
}
