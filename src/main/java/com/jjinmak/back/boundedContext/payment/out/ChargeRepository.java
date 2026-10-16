package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChargeRepository extends JpaRepository<Charge, Long> {

    Optional<Charge> findByPgOrderId(String pgOrderId);

    Optional<Charge> findByPaymentKey(String paymentKey);

    /** 같은 결제에 대해 아직 승인 전인 충전 요청이 있으면 재사용한다 (결제창을 닫고 다시 주문하기를 누른 경우) */
    Optional<Charge> findFirstByPaymentAndStatusOrderByCreatedAtDesc(Payment payment, ChargeStatus status);

    /** 복구 스케줄러용: 특정 상태에 너무 오래 머문 건 */
    List<Charge> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(ChargeStatus status, LocalDateTime before);

    /** 복구 스케줄러용: 돈은 들어왔는데 홀딩이 안 된 주문 충전 */
    List<Charge> findTop100ByPurposeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            ChargePurpose purpose, ChargeStatus status, LocalDateTime before);
}
