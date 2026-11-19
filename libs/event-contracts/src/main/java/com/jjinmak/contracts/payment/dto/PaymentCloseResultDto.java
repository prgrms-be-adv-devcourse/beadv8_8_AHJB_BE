package com.jjinmak.contracts.payment.dto;

/**
 * closePayment 의 결과. 주문은 status 를 보고 주문 그룹을 버릴지 정한다.
 *
 * CLOSED       : 결제를 닫았다 (이미 닫혀 있던 경우도 같다). 이 orderGroupId 로는 더 이상 승인도 결제 요청도 되지 않는다
 *                → 주문 그룹을 버려도 된다
 * NOT_FOUND    : 이 orderGroupId 로 만들어진 결제가 없다 (결제 요청 전이었다)
 *                → 주문 그룹을 버려도 된다. 단, 버린 뒤에 같은 orderGroupId 로 결제 요청을 보내면 결제가 새로 만들어진다
 * ALREADY_PAID : 이미 결제가 끝났다. 닫지 않았다. 결제 완료 이벤트(PaymentPaidEvent)가 발행됐거나 곧 도착한다
 *                → 주문 그룹을 버리면 안 된다
 *
 * paymentId 는 NOT_FOUND 일 때 null 이다.
 */
public record PaymentCloseResultDto(
        Status status,
        Long orderGroupId,
        Long paymentId
) {
    public enum Status { CLOSED, NOT_FOUND, ALREADY_PAID }

    public static PaymentCloseResultDto closed(Long orderGroupId, Long paymentId) {
        return new PaymentCloseResultDto(Status.CLOSED, orderGroupId, paymentId);
    }

    public static PaymentCloseResultDto notFound(Long orderGroupId) {
        return new PaymentCloseResultDto(Status.NOT_FOUND, orderGroupId, null);
    }

    public static PaymentCloseResultDto alreadyPaid(Long orderGroupId, Long paymentId) {
        return new PaymentCloseResultDto(Status.ALREADY_PAID, orderGroupId, paymentId);
    }

    public boolean isAlreadyPaid() {
        return status == Status.ALREADY_PAID;
    }

    // 주문 그룹을 버려도 되는지 (닫혔거나 처음부터 결제가 없었다)
    public boolean canDiscardOrderGroup() {
        return status == Status.CLOSED || status == Status.NOT_FOUND;
    }
}
