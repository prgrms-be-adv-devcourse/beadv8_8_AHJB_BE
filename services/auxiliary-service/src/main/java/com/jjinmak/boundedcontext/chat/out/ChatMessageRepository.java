package com.jjinmak.boundedcontext.chat.out;

import com.jjinmak.boundedcontext.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 여러 방의 안 읽은 메시지 수를 한 번에 센다.
     * 안 읽은 메시지 = 내가 보내지 않은 것(시스템 메시지 포함) 중 내 마지막 읽은 id보다 뒤의 것.
     * 내 마지막 읽은 id는 방에서 내 역할(winner/seller)에 따라 고른다. 아직 안 읽었으면 0.
     */
    @Query("""
            select new com.jjinmak.boundedcontext.chat.out.ChatUnreadCount(m.roomId, count(m))
            from ChatMessage m
            join ChatRoom r on r.id = m.roomId
            where m.roomId in :roomIds
              and (m.senderId is null or m.senderId <> :memberId)
              and m.id > coalesce(case when r.winnerId = :memberId then r.winnerLastReadId else r.sellerLastReadId end, 0)
            group by m.roomId
            """)
    List<ChatUnreadCount> countUnreadByRooms(@Param("memberId") Long memberId, @Param("roomIds") List<Long> roomIds);
    Page<ChatMessage> findByRoomId(Long roomId, Pageable pageable);
    boolean existsByIdAndRoomId(Long id, Long roomId);
}