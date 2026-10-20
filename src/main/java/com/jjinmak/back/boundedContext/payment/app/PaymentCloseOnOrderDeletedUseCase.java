package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.REQUESTED;

/**
 * 주문 그룹이 삭제됐을 때(결제 기한 만료 등) 그 그룹의 미완료 결제를 닫는다. (OrderGroupDeletedEvent → PaymentEventListener → WalletFacade → 여기)
 *
 *   PENDING / CHARGING → FAILED (사유 기록) + 승인 전 충전 요청(REQUESTED) → ABORTED
 *   PAID               → 건드리지 않고 로그만. 결제 완료 이벤트가 이미 주문으로 나갔으므로 주문이 환불 흐름으로 처리한다
 *   FAILED / 없음       → 아무 것도 하지 않는다 (멱등)
 *
 * FAILED 가 되면 이후 승인 API 는 토스 승인 전에 INVALID_PAYMENT_STATUS 로 거절되므로 돈이 움직이지 않는다.
 * 충전 요청을 ABORTED 로 닫는 이유: 복구 스케줄러가 토스에 다시 물어볼 필요가 없고, 사용자가 결제창 인증까지 마쳤더라도
 * 우리가 승인하지 않으면 토스 결제는 일어나지 않고 만료된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCloseOnOrderDeletedUseCase {

    public static final String ORDER_DELETED_CODE = "ORDER_DELETED";
    public static final String ORDER_DELETED_REASON = "주문 그룹이 삭제되어 결제가 종료됨";

    private final PaymentRepository paymentRepository;
    private final ChargeRepository chargeRepository;

    public void close(Long orderGroupId) {
        Payment payment = paymentRepository.findByOrderGroupId(orderGroupId).orElse(null);
        if (payment == null) return;                                   // 결제 요청 전에 삭제된 그룹

        if (payment.isPaid()) {
            log.warn("삭제된 주문 그룹에 완료된 결제가 있음. 주문이 환불로 처리해야 함. orderGroupId={} paymentId={}",
                    orderGroupId, payment.getId());
            return;
        }
        if (payment.isFailed()) return;                                // 중복 수신

        LocalDateTime now = LocalDateTime.now();
        payment.markFailed(now, ORDER_DELETED_REASON);

        for (Charge charge : chargeRepository.findByPaymentOrderByIdDesc(payment)) {
            if (charge.getStatus() == REQUESTED) {
                charge.markAborted(now, ORDER_DELETED_CODE, ORDER_DELETED_REASON);
            }
        }
        log.info("주문 그룹 삭제로 결제 종료: orderGroupId={} paymentId={}", orderGroupId, payment.getId());
    }
}
