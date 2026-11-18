package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.back.payment.app.support.PgApprovalSupport;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.payment.app.support.HoldingCreator;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.*;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_BALANCE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;

/**
 * 주문 결제 승인 (토스 결제창에서 결제하고 돌아온 뒤). (파사드 트랜잭션 안에서 실행)
 * 클라이언트가 결제 API 로 직접 호출한다. 주문 그룹 id 는 받지 않고 pgOrderId → Charge → Payment 로 찾는다.
 * 부족분 충전(FOR_PAYMENT)과 직접 결제(DIRECT_PAYMENT)가 같은 API 를 쓴다.
 *
 *   검증(Charge 존재·소유자·용도·상태·금액·지금 결제 계획과 일치·포인트/예치금 잔액) → 토스 승인
 *     부족분 충전 : 지갑 입금(충전 금액) → 포인트 · 예치금 차감 + Holding → Charge USED → Payment PAID
 *     직접 결제   : Charge USED (예치금을 거치지 않음) → 포인트 차감 + 토스 입금분을 ESCROW 에 기록 + Holding
 *                  → Payment PAID (토스가 알려준 실제 결제수단 저장)
 *   → PaymentPaidEvent 발행 (주문이 AFTER_COMMIT 으로 받아 그룹을 결제 완료로)
 *
 * 한 트랜잭션이므로 "돈은 들어왔는데 홀딩이 안 된" 상태는 DB 에 남지 않는다.
 * 토스 승인 뒤 DB 반영이 실패하면 토스 결제를 취소(보상)하고 예외를 올린다. Payment 는 CHARGING 에 남아 다시 주문하기가 가능하다.
 * 결제창에 있는 동안 포인트나 예치금이 줄어 결제할 수 없게 됐으면 토스 승인 전에 거절한다 (돈이 움직이지 않는다).
 * 승인 직전에 줄어든 경우는 홀딩에서 실패해 위의 보상(토스 취소)으로 처리된다.
 * 멱등: 이미 PAID 면 아무 것도 하지 않고 결과만 돌려준다 (이벤트도 다시 발행하지 않는다).
 *
 * Payment 를 FOR UPDATE 로 읽어 닫기(PaymentCloseUseCase)와 겹치지 않게 한다. 닫기가 먼저면 여기서 PAYMENT_CLOSED 로 거절하고,
 * 승인이 먼저면 닫기는 승인이 끝난 뒤 PAID 를 본다. "토스는 승인했는데 우리는 닫힌" 상태가 생기지 않는다.
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
        if (!charge.isBoundToPayment() || charge.getPayment() == null) {
            throw new BusinessException(CHARGE_PAYMENT_MISMATCH);      // 수동 충전 건을 주문 승인 API 로 보냄
        }

        // 여기서부터 트랜잭션이 끝날 때까지 이 결제의 닫기 · 결제 요청 · 다른 승인은 기다린다 (토스 호출 포함)
        Payment payment = paymentRepository.findByIdForUpdate(charge.getPayment().getId())
                .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));

        if (payment.isPaid()) {
            return PaymentSummaryDto.from(payment);                     // 멱등 (새로고침 등 중복 호출)
        }
        if (payment.isFailed()) {
            throw new BusinessException(PAYMENT_CLOSED);                // 주문이 버린 그룹의 결제창. 토스를 부르지 않는다
        }
        if (!payment.isCharging()) {
            throw new BusinessException(INVALID_PAYMENT_STATUS);
        }
        if (charge.getStatus() != REQUESTED) {
            throw new BusinessException(INVALID_CHARGE_STATUS);        // 이미 ABORTED 등. 다시 주문하기로 새 결제 요청
        }
        charge.validateAmount(amount);
        if (!charge.matchesPaymentPlan()) {
            throw new BusinessException(CHARGE_SUPERSEDED);            // 수단·포인트를 바꿔 다시 주문하기 전의 결제창
        }

        Wallet wallet = walletSupport.getWalletByHolderId(charge.getHolderId());
        validateFunds(wallet, payment, charge);

        TossPaymentResponse approved = pgApproval.approve(charge, paymentKey);

        try {
            LocalDateTime now = LocalDateTime.now();
            if (charge.isDirectPayment()) {
                charge.markPaidDirectly(approved.paymentKey(), approved.approvedAtLocal(), now, approved.method(), approved.receiptUrl());
                holdingCreator.hold(PaymentRequestUseCase.toHoldDto(payment));
                payment.markPaid(now, approved.method());
            } else {
                ledgerRecorder.deposit(wallet, CHARGE, charge.getAmount(), PAYMENT, charge.getId());
                charge.markCharged(approved.paymentKey(), approved.approvedAtLocal(), now, approved.method(), approved.receiptUrl());
                holdingCreator.hold(PaymentRequestUseCase.toHoldDto(payment), charge);   // 이 충전이 먼저 소비된다
                charge.markUsed(now);
                payment.markPaid(now);
            }
        } catch (RuntimeException e) {
            pgApproval.cancelApproved(charge, approved.paymentKey(), "주문 결제 반영 실패로 자동 취소");
            throw e;
        }

        eventPublisher.publish(PaymentPaidEvent.of(
                payment.getOrderGroupId(), payment.getId(), payment.getHolderId(), payment.getTotalPrice(), payment.getPaidAt()));

        return PaymentSummaryDto.from(payment);
    }

    // 결제 요청 뒤 결제창에 있는 동안 포인트나 예치금이 줄었으면 토스 승인 전에 막는다
    private void validateFunds(Wallet wallet, Payment payment, Charge charge) {
        if (!wallet.hasEnoughPoint(payment.getPointAmount())) {
            throw new BusinessException(INSUFFICIENT_POINT);
        }
        long depositAfterCharge = wallet.getBalance() + (charge.isDirectPayment() ? 0L : charge.getAmount());
        if (depositAfterCharge < payment.getDepositAmount()) {
            throw new BusinessException(INSUFFICIENT_BALANCE);
        }
    }
}
