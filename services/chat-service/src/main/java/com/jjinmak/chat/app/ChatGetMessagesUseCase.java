package com.jjinmak.chat.app;

import com.jjinmak.chat.domain.ChatMessage;
import com.jjinmak.chat.in.dto.ChatMessageDto;
import com.jjinmak.chat.out.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatGetMessagesUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final ChatGetRoomUseCase chatGetRoomUseCase;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 채팅방의 메시지를 최신순으로 조회한다. 그 방의 참여자만 볼 수 있다.
     *
     * @param memberId - 조회하는 회원 ID (로그인 회원)
     * @param roomId - 채팅방 ID
     * @param page - 페이지 번호 (0부터)
     * @param size - 페이지 크기 (1~50)
     * @return Page<ChatMessageDto> - 메시지 페이지 (mine = 내가 보낸 메시지인지)
     */
    public Page<ChatMessageDto> getMessages(Long memberId, Long roomId, int page, int size) {
        chatGetRoomUseCase.getParticipantRoom(memberId, roomId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id")
        );

        return chatMessageRepository.findByRoomId(roomId, pageable)
                .map(message -> message.dto(memberId));
    }
}