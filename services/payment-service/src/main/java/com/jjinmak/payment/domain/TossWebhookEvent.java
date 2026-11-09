package com.jjinmak.payment.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.payment.domain.enums.WebhookEventStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 토스페이먼츠가 보낸 웹훅 1건 (수신함).
 *
 * 웹훅은 받은 즉시 이 엔티티로 저장하고 200 을 돌려준 뒤, 처리는 따로 한다.
 * 처리 중 실패해도 기록이 남아 서버 안에서 다시 시도할 수 있고, 토스와 상태가 어긋났을 때 무엇을 받았는지 확인할 수 있다.
 *
 * - eventType  : PAYMENT_STATUS_CHANGED, DEPOSIT_CALLBACK 등. 토스가 이벤트를 추가할 수 있어 enum 이 아닌 문자열로 둔다
 * - paymentKey : 토스 결제 키. DEPOSIT_CALLBACK 처럼 본문에 없는 이벤트도 있어 nullable
 * - orderId    : 토스에 보낸 주문번호(= pgOrderId)
 * - rawBody    : 받은 본문 원문. DEPOSIT_CALLBACK 의 secret 같은 검증 값은 저장 전에 마스킹한다
 *
 * 웹훅 본문에는 eventId 가 없어 같은 내용이 여러 번 올 수 있다.
 * 중복은 저장 단계에서 막지 않고, 처리 단계에서 토스 조회 결과와 현재 상태를 비교해 IGNORED 로 걸러낸다.
 */
@Entity
@Table(
        name = "toss_webhook_event",
        indexes = {
                @Index(name = "idx_toss_webhook_status", columnList = "status, created_at"),
                @Index(name = "idx_toss_webhook_order", columnList = "order_id")
        }
)
@Getter
@NoArgsConstructor
public class TossWebhookEvent extends BaseIdAndTime {

    private static final int MAX_ERROR_LENGTH = 500;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(length = 200)
    private String paymentKey;

    @Column(length = 64)
    private String orderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawBody;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WebhookEventStatus status;

    // 처리 시도 횟수 (실패할 때마다 1 증가)
    @Column(nullable = false)
    private int retryCount;

    // PROCESSED 또는 IGNORED 가 된 시각
    private LocalDateTime processedAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;


    private TossWebhookEvent(String eventType, String paymentKey, String orderId, String rawBody) {
        this.eventType = eventType;
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.rawBody = rawBody;
        this.status = WebhookEventStatus.RECEIVED;
        this.retryCount = 0;
    }

    // 웹훅 수신 직후 저장할 때
    public static TossWebhookEvent receive(String eventType, String paymentKey, String orderId, String rawBody) {
        return new TossWebhookEvent(eventType, paymentKey, orderId, rawBody);
    }

    // 처리할 수 있는 상태인지 (처음 받았거나, 이전 시도가 실패한 건)
    public boolean isProcessable() {
        return !status.isDone();
    }

    // 토스 조회 결과를 결제·충전에 반영 완료
    public void markProcessed(LocalDateTime now) {
        if (status.isDone()) return;
        this.status = WebhookEventStatus.PROCESSED;
        this.processedAt = now;
        this.lastError = null;
    }

    // 반영할 것이 없음 (이미 같은 상태, 중복 수신, 처리 대상 아님)
    public void markIgnored(LocalDateTime now, String reason) {
        if (status.isDone()) return;
        this.status = WebhookEventStatus.IGNORED;
        this.processedAt = now;
        this.lastError = truncate(reason);
    }

    // 처리 실패. 재시도 횟수를 올리고 원인을 남긴다
    public void markFailed(String error) {
        if (status.isDone()) return;
        this.status = WebhookEventStatus.FAILED;
        this.retryCount++;
        this.lastError = truncate(error);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) return value;
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
