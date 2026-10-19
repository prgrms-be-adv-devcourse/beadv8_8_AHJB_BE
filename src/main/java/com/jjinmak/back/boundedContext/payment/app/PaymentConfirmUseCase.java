package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.boundedContext.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.*;

/**
 * 주문 결제 승인 (부족분을 토스 결제창에서 결제하고 돌아온 뒤). (파사드 트랜잭션 안에서 실행)
 * 클라이언트가 결제 API 로 직접 호출한다. 주문 그룹 id 는 받지 않고 pgOrderId → Charge → Payment 로 찾는다.
 *
 *   검증(Charge 존재·소유자·용도·상태·금액) → 토스 승인 → 지갑 입금(부족분) + 차감·Holding + Charge USED + Payment PAID
 *   → PaymentPaidEvent 발행 (주문이 AFTER_COMMIT 으로 받아 그룹을 결제 완료로)
 *
 * 한 트랜잭션이므로 "충전은 됐는데 홀딩이 안 된" 상태는 DB 에 남지 않는다.
 * 토스 승인 뒤 DB 반영이 실패하면 토스 결제를 취소(보상)하고 예외를 올린다. Payment 는 CHARGING 에 남아 다시 주문하기가 가능하다.
 * 멱등: 이미 PAID 면 아무 것도 하지 않고 결과만 돌려준다 (이벤트도 다시 발행하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmUseCase {

    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final WalletSupport walletSupport;
    private final LedgerRecorder ledgerRecorder;
    private final HoldingCreator holdingCreator;
    private final PgApprovalSupport pgApproval;
    private final EventPublisher eventPublisher;

    public PaymentSummaryDto confirm(Long holderId, String pgOrderId, String paymentKey, long amount) {
        Charge charge = chargeRepository.findByPgOrderId(pgOrderId)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        if (!charge.getHolderId().equals(holderId)) {
            throw new BusinessException(CHARGE_FORBIDDEN);
        }
        if (!charge.isForPayment() || charge.getPayment() == null) {
            throw new BusinessException(CHARGE_PAYMENT_MISMATCH);      // 수동 충전 건을 주문 승인 API 로 보냄
        }

        Payment payment = paymentRepository.findWithItemsById(charge.getPayment().getId())
                .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));

        if (payment.isPaid()) {
            return PaymentSummaryDto.from(payment);                     // 멱등 (새로고침 등 중복 호출)
        }
        if (!payment.isCharging()) {
            throw new BusinessException(INVALID_PAYMENT_STATUS);
        }
        if (charge.getStatus() != REQUESTED) {
            throw new BusinessException(INVALID_CHARGE_STATUS);        // 이미 ABORTED 등. 다시 주문하기로 새 충전 요청
        }
        charge.validateAmount(amount);

        TossPaymentResponse approved = pgApproval.approve(charge, paymentKey);

        try {
            LocalDateTime now = LocalDateTime.now();

            Wallet wallet = walletSupport.getWalletByHolderId(charge.getHolderId());
            ledgerRecorder.deposit(wallet, CHARGE, charge.getAmount(), PAYMENT, charge.getId());
            charge.markCharged(approved.paymentKey(), approved.approvedAtLocal(), now, approved.method(), approved.receiptUrl());

            holdingCreator.hold(PaymentRequestUseCase.toHoldDto(payment), charge);   // 이 충전이 먼저 소비된다

            charge.markUsed(now);
            payment.markPaid(now);
        } catch (RuntimeException e) {
            pgApproval.cancelApproved(charge, approved.paymentKey(), "주문 결제 반영 실패로 자동 취소");
            throw e;
        }

        eventPublisher.publish(PaymentPaidEvent.of(
                payment.getOrderGroupId(), payment.getId(), payment.getHolderId(), payment.getTotalPrice(), payment.getPaidAt()));

        return PaymentSummaryDto.from(payment);
    }
}
