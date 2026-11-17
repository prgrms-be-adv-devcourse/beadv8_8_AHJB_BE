package com.jjinmak.boundedcontext.chat.out;

import com.jjinmak.boundedcontext.chat.domain.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
}