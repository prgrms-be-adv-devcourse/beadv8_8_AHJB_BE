package com.jjinmak.boundedContext.chat.out;

import com.jjinmak.boundedContext.chat.domain.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
}