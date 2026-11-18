package com.jjinmak.boundedcontext.chat.domain;

import com.jjinmak.boundedcontext.chat.in.dto.ChatRoomDetailDto;
import com.jjinmak.boundedcontext.chat.in.dto.ChatRoomItemDto;
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

    /** 목록 미리보기용 마지막 메시지 스냅샷 (매번 chat_message를 조회하지 않으려고) */
    @Column(length = 1000)
    private String lastMessageContent;

    /** 각자 마지막으로 읽은 메시지 id. null = 아직 아무것도 안 읽음 */
    private Long winnerLastReadId;

    private Long sellerLastReadId;

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

    /** 메시지가 저장될 때 목록 정렬·미리보기 값을 갱신한다 */
    public void touch(ChatMessage message) {
        this.lastMessageAt = message.getCreatedAt();
        this.lastMessageContent = message.getContent();
    }

    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }

    public ChatRole roleOf(Long memberId) {
        return winnerId.equals(memberId) ? ChatRole.WINNER : ChatRole.SELLER;
    }

    public Long counterpartId(Long memberId) {
        return winnerId.equals(memberId) ? sellerId : winnerId;
    }

    /** 이 회원이 마지막으로 읽은 메시지 id (없으면 null) */
    public Long lastReadIdOf(Long memberId) {
        return winnerId.equals(memberId) ? winnerLastReadId : sellerLastReadId;
    }

    /** 상대가 마지막으로 읽은 메시지 id. 내 메시지 id가 이 값 이하이면 "읽음" */
    public Long counterpartLastReadId(Long memberId) {
        return lastReadIdOf(counterpartId(memberId));
    }

    /**
     * 읽은 위치를 앞으로만 옮긴다. 뒤로 가는 요청(늦게 도착한 옛 요청, 중복 요청)은 무시한다.
     *
     * @return 실제로 바뀌었으면 true (상대에게 READ push를 보낼지 결정하는 데 쓴다)
     */
    public boolean markRead(Long memberId, Long messageId) {
        Long current = lastReadIdOf(memberId);
        if (messageId == null || (current != null && messageId <= current)) {
            return false;
        }
        if (winnerId.equals(memberId)) {
            winnerLastReadId = messageId;
        } else {
            sellerLastReadId = messageId;
        }
        return true;
    }

    public ChatRoomItemDto itemDto(Long memberId, String counterpartNickname) {
        return new ChatRoomItemDto(
                getId(),
                productName,
                productThumbnailUrl,
                counterpartNickname,
                roleOf(memberId),
                status,
                lastMessageAt
        );
    }

    public ChatRoomDetailDto detailDto(Long memberId, String counterpartNickname) {
        return new ChatRoomDetailDto(
                getId(),
                auctionId,
                productId,
                productName,
                productThumbnailUrl,
                counterpartNickname,
                roleOf(memberId),
                status,
                lastMessageAt,
                getCreatedAt()
        );
    }
}