package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.enums.PaymentCloseReason;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.payment.domain.enums.PaymentCloseReason.CLOSED_BY_ORDER;
import static com.jjinmak.back.payment.domain.enums.PaymentCloseReason.ORDER_DELETED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;

/**
 * 주문이 버린 주문 그룹의 미완료 결제를 닫는다. (파사드 트랜잭션 안에서 실행)
 *
 * 들어오는 길
 *   closeByOrder        : 주문이 그룹을 버리면서 결제 포트로 바로 닫는다 (결제창 닫힘 등. PaymentPort.closePayment)
 *   closeOnOrderDeleted : 주문 그룹이 삭제됐다는 이벤트를 받고 닫는다 (결제 기한 만료 등. OrderGroupDeletedEvent)
 *
 * 결제 상태별 처리
 *   PENDING / CHARGING → FAILED (사유 기록) + 승인 전 결제창(Charge REQUESTED) → ABORTED     → CLOSED
 *   FAILED             → 아무 것도 하지 않는다 (멱등)                                        → CLOSED
 *   PAID               → 닫지 않는다. 결제 완료 이벤트가 이미 주문으로 나갔다                    → ALREADY_PAID
 *   없음                → 결제 요청 전에 버려진 그룹                                           → NOT_FOUND
 *
 * FAILED 가 되면 이후 승인 API 와 같은 orderGroupId 의 결제 요청은 PAYMENT_CLOSED 로 거절되므로 돈이 움직이지 않는다.
 * 충전 요청을 ABORTED 로 닫는 이유: 복구 스케줄러가 토스에 다시 물어볼 필요가 없고, 사용자가 결제창 인증까지 마쳤더라도
 * 우리가 승인하지 않으면 토스 결제는 일어나지 않고 만료된다.
 *
 * Payment 를 FOR UPDATE 로 읽는다. 승인이 토스를 호출하는 중이면 그 승인이 끝날 때까지 기다렸다가 PAID 를 보고 ALREADY_PAID 를 돌려준다.
 * 반대로 여기가 먼저면 승인은 락을 얻은 뒤 FAILED 를 보고 토스를 부르기 전에 거절한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCloseUseCase {

    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;

    // buyerId 가 결제의 구매자가 아니면 PAYMENT_FORBIDDEN
    public PaymentCloseResultDto closeByOrder(Long orderGroupId, Long buyerId) {
        return close(orderGroupId, buyerId, true, CLOSED_BY_ORDER);
    }

    // 이벤트에는 구매자가 없으므로 소유자를 확인하지 않는다
    public PaymentCloseResultDto closeOnOrderDeleted(Long orderGroupId) {
        PaymentCloseResultDto result = close(orderGroupId, null, false, ORDER_DELETED);
        if (result.isAlreadyPaid()) {
            log.warn("삭제된 주문 그룹에 완료된 결제가 있음. 주문이 환불로 처리해야 함. orderGroupId={} paymentId={}",
                    orderGroupId, result.paymentId());
        }
        return result;
    }

    private PaymentCloseResultDto close(Long orderGroupId, Long buyerId, boolean checkOwner, PaymentCloseReason reason) {
        Payment payment = paymentRepository.findByOrderGroupIdForUpdate(orderGroupId).orElse(null);
        if (payment == null) {
            return PaymentCloseResultDto.notFound(orderGroupId);
        }
        if (checkOwner && !payment.getHolderId().equals(buyerId)) {
            throw new BusinessException(PAYMENT_FORBIDDEN);
        }
        if (payment.isPaid()) {
            return PaymentCloseResultDto.alreadyPaid(orderGroupId, payment.getId());
        }
        if (payment.isFailed()) {
            return PaymentCloseResultDto.closed(orderGroupId, payment.getId());       // 중복 호출 · 중복 수신
        }

        LocalDateTime now = LocalDateTime.now();
        payment.markFailed(now, reason.message());

        for (Charge charge : chargeRepository.findByPaymentOrderByIdDesc(payment)) {
            if (charge.getStatus() == REQUESTED) {
                charge.markAborted(now, reason.code(), reason.message());
            }
        }
        log.info("결제 종료: orderGroupId={} paymentId={} reason={}", orderGroupId, payment.getId(), reason.code());
        return PaymentCloseResultDto.closed(orderGroupId, payment.getId());
    }
}
