package com.jjinmak.chat.app;

import com.jjinmak.chat.domain.ChatMessage;
import com.jjinmak.chat.domain.ChatRoom;
import com.jjinmak.chat.exception.ChatErrorCode;
import com.jjinmak.chat.in.dto.ChatMessagePushDto;
import com.jjinmak.chat.out.ChatMessageRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSendMessageUseCase {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final ChatGetRoomUseCase chatGetRoomUseCase;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 채팅방에 메시지를 저장한다. 그 방의 참여자만, 방이 OPEN일 때만 보낼 수 있다.
     * 저장 후 방의 마지막 메시지 시각을 갱신한다 (목록 정렬용).
     *
     * @param memberId - 보내는 회원 ID (WebSocket 세션의 Principal)
     * @param roomId - 채팅방 ID
     * @param content - 메시지 내용 (1~1000자, 공백만은 불가)
     * @return ChatMessagePushDto - 구독자에게 뿌릴 메시지 (보낸 사람은 역할로만 표시)
     */
    public ChatMessagePushDto sendMessage(Long memberId, Long roomId, String content) {
        ChatRoom room = chatGetRoomUseCase.getParticipantRoom(memberId, roomId);

        if (!room.isOpen()) {
            throw new BusinessException(ChatErrorCode.ROOM_CLOSED);
        }
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ChatErrorCode.INVALID_MESSAGE);
        }

        ChatMessage message = chatMessageRepository.save(new ChatMessage(roomId, memberId, content.strip()));
        room.touch(message.getCreatedAt());

        return message.pushDto(room.roleOf(memberId));
    }
}