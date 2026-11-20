package com.jjinmak.boundedcontext.market.auction.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 경매 외부 이벤트 아웃박스 (#548, 팀 공통 아웃박스 구조)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionOutbox extends BaseIdAndTime {

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime publishedAt;

    public AuctionOutbox(String topic, String messageKey, String payload){
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public void published(LocalDateTime publishedAt){
        this.publishedAt = publishedAt;
    }
}
