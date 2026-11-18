package com.jjinmak.boundedcontext.chat.app;

import com.jjinmak.boundedcontext.chat.in.dto.ChatUnreadCountDto;
import com.jjinmak.boundedcontext.chat.out.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatGetUnreadCountUseCase {

    private final ChatRoomRepository chatRoomRepository;

    /**
     * 회원이 참여한 방 중 안 읽은 메시지가 있는 방의 수를 센다 (헤더 말풍선 배지).
     * 메시지 수가 아니라 방 수인 것은 "확인할 대화가 몇 개인지"를 보여주기 위해서다.
     *
     * @param memberId - 조회하는 회원 ID (로그인 회원)
     * @return ChatUnreadCountDto - 안 읽은 방 수
     */
    public ChatUnreadCountDto getUnreadCount(Long memberId) {
        return new ChatUnreadCountDto(chatRoomRepository.countRoomsWithUnread(memberId));
    }
}