package com.jjinmak.back.shared.payment.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 결제가 완료되었다는 사실. 결제 컨텍스트가 발행하고 주문 컨텍스트가 받는다.
 *
 * 발행 시점 : 결제 트랜잭션 안에서 publish 되며, 수신 측은 @TransactionalEventListener(AFTER_COMMIT) 로 받아
 *            결제가 커밋된 뒤에만 처리한다. 수신 측 실패는 결제를 롤백하지 않는다.
 * 발행 경로 : 전액 예치금 결제(requestPayment 에서 바로 PAID) / 결제창 승인(confirm) 두 경우 모두 같은 이벤트.
 * 수신 측  : 별도 트랜잭션(REQUIRES_NEW) + 멱등 (같은 orderGroupId 가 이미 결제 완료면 무시).
 *            주문 그룹이 결제 대기 상태가 아니면(취소됨 등) 환불 흐름으로.
 *
 * 나중에 컨텍스트가 분리되면 이 레코드가 그대로 메시지 본문이 된다. 그래서 엔티티 참조 없이 값만 담는다.
 *  - eventId   : 수신 측 중복 처리 방지용
 *  - occurredAt: 이벤트 생성 시각 (paidAt 과 같지 않을 수 있다 — 재발행 시)
 */
public record PaymentPaidEvent(
        String eventId,
        LocalDateTime occurredAt,
        Long orderGroupId,
        Long paymentId,
        Long buyerId,
        long totalPrice,
        LocalDateTime paidAt
) {
    public static PaymentPaidEvent of(Long orderGroupId, Long paymentId, Long buyerId, long totalPrice, LocalDateTime paidAt) {
        return new PaymentPaidEvent(UUID.randomUUID().toString(), LocalDateTime.now(),
                orderGroupId, paymentId, buyerId, totalPrice, paidAt);
    }
}
