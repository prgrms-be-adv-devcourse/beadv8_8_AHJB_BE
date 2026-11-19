package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.back.payment.app.support.PgApprovalSupport;
import com.jjinmak.back.payment.app.support.SystemWalletFinder;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.payment.domain.enums.ChargePurpose.DIRECT_PAYMENT;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.USED;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.PG_REFUND_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_REFUND_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_REFUND_RECEIVE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.REFUND_PAY;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.REFUND_RECEIVE;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_NOT_FOUND;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

/**
 * 주문 1건 환불 (전액). 결제한 수단 그대로 돌려준다. (WalletFacade 트랜잭션 안에서 실행)
 *
 *   홀딩의 금액 구성          돌려주는 곳                      원장
 *   포인트로 낸 금액      →   구매자 포인트                    POINT_REFUND_PAY / POINT_REFUND_RECEIVE
 *   예치금으로 낸 금액    →   구매자 예치금                    REFUND_PAY / REFUND_RECEIVE
 *   직접 결제한 금액      →   토스 부분 취소 (결제한 수단으로)    PG_REFUND_PAY 한 줄 (ESCROW → 토스)
 *
 * 직접 결제분은 예치금으로 돌려주지 않는다. 카드로 낸 돈이 출금할 수 있는 예치금이 되면 안 되기 때문이다.
 * 주문 묶음을 한 번에 결제했더라도 환불은 주문 단위이므로, 토스에는 이 주문 몫만 부분 취소한다.
 *
 * 순서: 홀딩 상태 변경 + 지갑 · 원장 반영 → DB 에 먼저 내보내기(flush) → 토스 취소.
 *   토스 취소를 마지막에 두어, 우리 쪽 반영이 실패하면(지갑 동시 수정 등) 토스를 부르지 않고 끝난다.
 *   토스 취소가 실패하면 예외로 롤백되어 우리 쪽 반영도 함께 되돌아간다. 환불 이벤트가 다시 오면 처음부터 다시 처리한다.
 *   토스 취소는 됐는데 커밋이 실패한 경우에도 같은 Idempotency-Key 로 다시 요청하므로 토스 취소는 한 번만 된다.
 *
 * 멱등: 이미 환불된 홀딩이면 돈을 움직이지 않고 환불 완료 이벤트만 다시 발행한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletRefundOrderUseCase {

    private static final String PG_REFUND_REASON = "주문 환불";

    private final SystemWalletFinder finder;
    private final HoldingRepository holdingRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;
    private final LedgerRecorder ledgerRecorder;
    private final PgApprovalSupport pgApproval;
    private final EventPublisher eventPublisher;

    public void refund(Long refundId, Long orderId, long requestedAmount) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        if (holding.isRefunded()) {
            publishRefunded(refundId, holding);
            return;
        }

        Wallet buyer = holding.getBuyerWallet();
        Wallet escrow = finder.escrowForUpdate();
        PaymentShare share = holding.getShare();

        holding.refund(requestedAmount);

        // 결제 기록에도 환불 시각을 남긴다. (Holding.paymentId 는 Payment 의 id)
        paymentRepository.findWithItemsById(holding.getPaymentId())
                .ifPresent(payment -> payment.markItemRefunded(orderId, holding.getRefundedAt()));

        String groupId = UUID.randomUUID().toString();
        if (share.pointAmount() > 0) {
            ledgerRecorder.transferDepositToPoint(escrow, POINT_REFUND_PAY, buyer, POINT_REFUND_RECEIVE,
                    share.pointAmount(), groupId, ORDER, orderId);
        }
        if (share.depositAmount() > 0) {
            ledgerRecorder.transfer(escrow, REFUND_PAY, buyer, REFUND_RECEIVE,
                    share.depositAmount(), groupId, ORDER, orderId);
        }
        if (share.pgAmount() > 0) {
            refundToPg(holding, escrow, share.pgAmount());
        }

        publishRefunded(refundId, holding);
    }

    // 직접 결제분: ESCROW 에서 빼고 토스로 이 주문 몫만 취소한다
    private void refundToPg(Holding holding, Wallet escrow, long pgAmount) {
        Charge charge = chargeRepository
                .findFirstByPayment_IdAndPurposeAndStatusOrderByIdDesc(holding.getPaymentId(), DIRECT_PAYMENT, USED)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        ledgerRecorder.withdraw(escrow, PG_REFUND_PAY, pgAmount, ORDER, holding.getOrderId());
        charge.cancelPartially(pgAmount, LocalDateTime.now());

        holdingRepository.flush();      // 우리 쪽 반영이 실패할 것이면 토스를 부르기 전에 실패하게 한다
        pgApproval.cancelPartially(charge, holding.getOrderId(), pgAmount, PG_REFUND_REASON);
    }

    private void publishRefunded(Long refundId, Holding holding) {
        eventPublisher.publish(new WalletRefundedEvent(
                refundId, holding.getOrderId(), holding.getPaymentId(), holding.getAmount(), holding.getRefundedAt()
        ));
    }
}
