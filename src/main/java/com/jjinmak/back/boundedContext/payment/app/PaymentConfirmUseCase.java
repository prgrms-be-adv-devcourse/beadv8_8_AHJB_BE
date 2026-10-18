package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.boundedContext.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.PaymentConfirmDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.*;

/**
 * 주문 결제 승인 (부족분을 토스 결제창에서 결제하고 돌아온 뒤). (파사드 트랜잭션 안에서 실행)
 *
 *   검증(소유자·상태·Charge 소속·금액) → 토스 승인 → 지갑 입금(부족분) + 차감·Holding + Charge USED + Payment PAID
 *
 * 한 트랜잭션이므로 "충전은 됐는데 홀딩이 안 된" 상태는 DB 에 남지 않는다.
 * 토스 승인 뒤 DB 반영이 실패하면 토스 결제를 취소(보상)하고 예외를 올린다. Payment 는 CHARGING 에 남아 다시 주문하기가 가능하다.
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

    public PaymentResultDto confirm(PaymentConfirmDto dto) {
        Payment payment = paymentRepository.findByOrderGroupId(dto.orderGroupId())
                .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));

        if (!payment.getHolderId().equals(dto.buyerId())) {
            throw new BusinessException(PAYMENT_FORBIDDEN);
        }
        if (payment.isPaid()) {
            return PaymentResultDto.paid(payment.getId(), payment.getTotalPrice());   // 멱등
        }
        if (!payment.isCharging()) {
            throw new BusinessException(INVALID_PAYMENT_STATUS);
        }

        Charge charge = chargeRepository.findByPgOrderId(dto.pgOrderId())
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        if (charge.getPayment() == null || !charge.getPayment().getId().equals(payment.getId())) {
            throw new BusinessException(CHARGE_PAYMENT_MISMATCH);
        }
        if (charge.getStatus() != REQUESTED) {
            throw new BusinessException(INVALID_CHARGE_STATUS);
        }
        charge.validateAmount(dto.amount());

        TossPaymentResponse approved = pgApproval.approve(charge, dto.paymentKey());

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

        return PaymentResultDto.paid(payment.getId(), payment.getTotalPrice());
    }
}
