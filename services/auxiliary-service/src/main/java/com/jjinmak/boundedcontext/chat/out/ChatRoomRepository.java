package com.jjinmak.boundedcontext.chat.out;

import com.jjinmak.boundedcontext.chat.domain.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    boolean existsByAuctionId(Long auctionId);

    @Query("""
            select r from ChatRoom r
            where r.winnerId = :memberId or r.sellerId = :memberId
            order by coalesce(r.lastMessageAt, r.createdAt) desc, r.id desc
            """)
    Page<ChatRoom> findByParticipant(@Param("memberId") Long memberId, Pageable pageable);

    /** 안 읽은 메시지가 하나라도 있는 방의 수 (헤더 배지). 읽은 위치 판단은 countUnreadByRooms와 같은 규칙 */
    @Query("""
            select count(r) from ChatRoom r
            where (r.winnerId = :memberId or r.sellerId = :memberId)
              and exists (
                  select 1 from ChatMessage m
                  where m.roomId = r.id
                    and (m.senderId is null or m.senderId <> :memberId)
                    and m.id > coalesce(case when r.winnerId = :memberId then r.winnerLastReadId else r.sellerLastReadId end, 0)
              )
            """)
    long countRoomsWithUnread(@Param("memberId") Long memberId);
}