package com.jjinmak.back.payment.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka 로 보낼 결제 이벤트 1건 (아웃박스).
 *
 * 결제 변경과 같은 트랜잭션에서 저장되므로, 결제가 커밋되면 이벤트도 반드시 남고 롤백되면 함께 사라진다.
 * 전송은 스케줄러(PaymentOutboxScheduler)가 따로 하고, 브로커 응답을 받은 뒤 publishedAt 을 기록한다.
 *
 * - topic / messageKey : 보낼 토픽과 메시지 키
 * - payload            : 이벤트를 JSON 으로 바꾼 본문. 그대로 Kafka 메시지 본문이 된다
 * - publishedAt        : 전송이 끝난 시각. 아직 보내지 않았으면 null
 */
@Entity
@Table(
        name = "payment_outbox",
        indexes = @Index(name = "idx_payment_outbox_published_at", columnList = "published_at, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutbox extends BaseIdAndTime {

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime publishedAt;

    public PaymentOutbox(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public void published(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
