package com.jjinmak.chat.app;

import com.jjinmak.chat.domain.ChatMember;
import com.jjinmak.chat.out.ChatMemberRepository;
import com.jjinmak.contracts.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSaveMemberUseCase {

    private final ChatMemberRepository chatMemberRepository;

    /**
     * 회원 생성 이벤트를 받아 닉네임 복제본을 저장한다.
     * 이미 있으면 닉네임만 맞춘다 (Kafka 중복 수신 대비, order-service의 syncMember와 같은 방식).
     *
     * @param member - 회원 ID, UUID, 닉네임
     */
    public void saveMember(MemberDto member) {
        ChatMember chatMember = chatMemberRepository.findById(member.id())
                .orElseGet(() -> new ChatMember(member.id(), member.uuid(), member.nickname()));

        chatMember.sync(member.nickname());

        chatMemberRepository.save(chatMember);
    }
}