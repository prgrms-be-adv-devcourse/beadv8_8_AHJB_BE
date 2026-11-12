package com.jjinmak.chat.out;

import com.jjinmak.chat.domain.ChatRoom;
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
}