package com.jjinmak.boundedcontext.market.shared.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// market 외부 이벤트 아웃박스 (#548, 팀 공통 아웃박스 구조). 상품·경매 이벤트를 함께 담는다.
// 저장은 MarketOutboxRecorder가 JDBC 일괄 INSERT로 하고, 조회·발행 표시는 이 엔티티로 한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketOutbox extends BaseIdAndTime {

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime publishedAt;

    public MarketOutbox(String topic, String messageKey, String payload){
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public void published(LocalDateTime publishedAt){
        this.publishedAt = publishedAt;
    }
}
