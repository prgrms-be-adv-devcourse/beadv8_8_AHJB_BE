package com.jjinmak.boundedcontext.chat.app;

import com.jjinmak.boundedcontext.chat.domain.ChatBlockReason;
import com.jjinmak.boundedcontext.chat.domain.ChatMessage;
import com.jjinmak.boundedcontext.chat.domain.ChatMessagePolicy;
import com.jjinmak.boundedcontext.chat.domain.ChatRoom;
import com.jjinmak.boundedcontext.chat.exception.ChatErrorCode;
import com.jjinmak.boundedcontext.chat.in.dto.ChatMessagePushDto;
import com.jjinmak.boundedcontext.chat.out.ChatMessageRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSendMessageUseCase {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final ChatGetRoomUseCase chatGetRoomUseCase;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 채팅방에 메시지를 저장한다. 그 방의 참여자만, 방이 OPEN일 때만 보낼 수 있다.
     * 전화번호·계좌·외부 채널·현금 거래 유도가 담긴 메시지는 저장하지 않고 CHAT007을 던진다
     * (보낸 사람에게만 안내, 상대에게는 가지 않음. 내용은 로그에 남기지 않는다).
     * 저장 후 방의 마지막 메시지 시각·내용을 갱신한다 (목록 정렬·미리보기용).
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

        Optional<ChatBlockReason> blocked = ChatMessagePolicy.check(content);
        if (blocked.isPresent()) {
            log.info("chat message blocked: roomId={}, memberId={}, reason={}", roomId, memberId, blocked.get());
            throw new BusinessException(ChatErrorCode.MESSAGE_BLOCKED);
        }

        ChatMessage message = chatMessageRepository.save(ChatMessage.text(roomId, memberId, content.strip()));
        room.touch(message);

        return message.pushDto(room.roleOf(memberId));
    }
}