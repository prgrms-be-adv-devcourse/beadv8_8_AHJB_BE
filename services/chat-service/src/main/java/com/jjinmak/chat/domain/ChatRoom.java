package com.jjinmak.chat.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_chat_room_winner_last_message", columnList = "winner_id, last_message_at"),
        @Index(name = "idx_chat_room_seller_last_message", columnList = "seller_id, last_message_at")
})
public class ChatRoom extends BaseIdAndTime {

    @NotNull
    @Column(unique = true)
    private Long auctionId;

    @NotNull
    private Long productId;

    @NotNull
    @Column(length = 100)
    private String productName;

    private String productThumbnailUrl;

    @NotNull
    private Long winnerId;

    @NotNull
    private Long sellerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ChatRoomStatus status;

    private LocalDateTime lastMessageAt;

    /**
     * 낙찰 이벤트로 거래 채팅방을 연다.
     * auctionId, productId, winnerId, sellerId는 auction.won 이벤트 값이고,
     * productName, productThumbnailUrl은 product.registered로 받아 둔 복제본(ChatProduct)에서 읽는다.
     */
    public ChatRoom(Long auctionId, Long productId, String productName, String productThumbnailUrl,
                    Long winnerId, Long sellerId) {
        this.auctionId = auctionId;
        this.productId = productId;
        this.productName = productName;
        this.productThumbnailUrl = productThumbnailUrl;
        this.winnerId = winnerId;
        this.sellerId = sellerId;
        this.status = ChatRoomStatus.OPEN;
    }

    public boolean isParticipant(Long memberId) {
        return winnerId.equals(memberId) || sellerId.equals(memberId);
    }

    public boolean isOpen() {
        return status == ChatRoomStatus.OPEN;
    }

    public void touch(LocalDateTime messageAt) {
        this.lastMessageAt = messageAt;
    }

    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }
}