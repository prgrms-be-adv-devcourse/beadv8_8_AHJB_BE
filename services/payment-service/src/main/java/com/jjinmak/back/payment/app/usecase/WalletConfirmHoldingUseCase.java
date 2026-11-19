package com.jjinmak.back.payment.app.usecase;


import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.app.support.PointEarnCalculator;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_EARN;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

/**
 * 구매 확정. (OrderConfirmPurchaseEvent → PaymentEventListener → WalletFacade → 여기)
 * 구매자가 직접 확정한 경우, 기간이 지나 자동 확정된 경우, 환불 요청이 거절되어 확정된 경우 모두 같은 이벤트로 들어온다.
 *
 *   홀딩 HELD → CONFIRMED + 구매자에게 포인트 적립 (원장 POINT_EARN, 홀딩에 적립 포인트 기록)
 *
 * 주문 대금은 정산 때까지 ESCROW 에 그대로 있고, 여기서는 구매자 포인트만 늘어난다.
 * 적립할 포인트가 0 이면(카드로 직접 결제, 포인트로 전액 결제 등) 원장 기록 없이 상태만 바꾼다.
 * 환불은 구매 확정 전에만 할 수 있으므로 적립한 포인트를 회수하는 경우는 없다.
 *
 * 멱등: 이미 구매 확정됐거나 정산까지 끝난 홀딩이면 아무 것도 하지 않는다 (다시 적립하지 않는다).
 * 원장 멱등키 "ORDER:{orderId}:POINT_EARN" 이 유니크라 같은 주문에 두 번 적립될 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletConfirmHoldingUseCase {

    private final HoldingRepository holdingRepository;
    private final PointEarnCalculator pointEarnCalculator;
    private final LedgerRecorder ledgerRecorder;

    public void confirm(Long orderId) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        // 이미 구매 확정됐거나 그 뒤 정산까지 끝난 홀딩이면 같은 이벤트를 다시 받은 것이므로 넘어간다 (멱등).
        // 환불된 홀딩이면 아래 confirm() 에서 예외가 난다. 환불된 주문의 구매 확정은 중복이 아니라 잘못된 요청이다
        if (holding.isConfirmed() || holding.isReleased()) return;

        long earnedPoint = pointEarnCalculator.earnOf(holding);
        holding.confirm(earnedPoint);

        if (earnedPoint > 0) {
            ledgerRecorder.depositPoint(holding.getBuyerWallet(), POINT_EARN, earnedPoint, ORDER, orderId);
        }
    }
}
