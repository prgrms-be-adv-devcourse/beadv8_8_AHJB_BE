package com.jjinmak.chat.out;

import com.jjinmak.chat.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    boolean existsByAuctionId(Long auctionId);
}