package com.jjinmak.back.settlement.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 정산 이벤트 아웃박스. 정산 변경과 같은 트랜잭션에 저장되고 스케줄러가 Kafka 로 보낸다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementOutbox extends BaseIdAndTime {

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime publishedAt;

    public SettlementOutbox(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public void published(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}