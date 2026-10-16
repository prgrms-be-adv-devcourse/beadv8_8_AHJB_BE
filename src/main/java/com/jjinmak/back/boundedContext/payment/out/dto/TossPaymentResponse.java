package com.jjinmak.back.boundedContext.payment.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 토스 Payment 객체 중 우리가 쓰는 필드만. 나머지는 무시한다.
 *
 * status : READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED
 * approvedAt : ISO 8601 (타임존 오프셋 포함) 문자열. 예: 2026-10-15T14:03:21+09:00
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String orderName,
        String status,
        long totalAmount,
        long balanceAmount,
        String method,
        String requestedAt,
        String approvedAt,
        String lastTransactionKey,
        Failure failure
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Failure(String code, String message) {}

    public boolean isDone() {
        return "DONE".equals(status);
    }

    public boolean isCanceled() {
        return "CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status);
    }

    /** ABORTED / EXPIRED 처럼 다시 승인될 수 없는 상태 */
    public boolean isDead() {
        return "ABORTED".equals(status) || "EXPIRED".equals(status);
    }

    public LocalDateTime approvedAtLocal() {
        return approvedAt == null ? null : OffsetDateTime.parse(approvedAt).toLocalDateTime();
    }

    public String failureCode() {
        return failure == null ? null : failure.code();
    }

    public String failureMessage() {
        return failure == null ? null : failure.message();
    }
}
