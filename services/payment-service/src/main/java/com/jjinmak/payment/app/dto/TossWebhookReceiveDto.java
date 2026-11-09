package com.jjinmak.payment.app.dto;

/**
 * 토스 웹훅 수신값. 컨트롤러(in/)가 본문을 파싱하고 검증 값(secret)을 마스킹한 뒤 만든다.
 *
 * - eventType  : PAYMENT_STATUS_CHANGED, DEPOSIT_CALLBACK 등. 파싱에 실패하면 UNKNOWN
 * - paymentKey : 토스 결제 키. 본문에 없으면 null
 * - orderId    : 토스에 보낸 주문번호(= pgOrderId). 본문에 없으면 null
 * - rawBody    : 마스킹된 본문 원문
 */
public record TossWebhookReceiveDto(
        String eventType,
        String paymentKey,
        String orderId,
        String rawBody
) {
}
