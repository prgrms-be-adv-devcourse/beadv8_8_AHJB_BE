package com.jjinmak.chat.out;

import com.jjinmak.chat.domain.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
}