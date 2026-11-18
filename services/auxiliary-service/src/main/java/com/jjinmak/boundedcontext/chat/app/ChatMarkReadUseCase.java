package com.jjinmak.boundedcontext.chat.app;

import com.jjinmak.boundedcontext.chat.domain.ChatRoom;
import com.jjinmak.boundedcontext.chat.exception.ChatErrorCode;
import com.jjinmak.boundedcontext.chat.in.dto.ChatReadResultDto;
import com.jjinmak.boundedcontext.chat.out.ChatMessageRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMarkReadUseCase {

    private final ChatGetRoomUseCase chatGetRoomUseCase;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 회원이 채팅방을 어디까지 읽었는지 기록한다. 그 방의 참여자만 가능하다.
     * 읽은 위치는 앞으로만 움직이므로 같은 요청이 두 번 오거나 옛 요청이 늦게 와도 결과가 같다.
     *
     * @param memberId - 읽은 회원 ID
     * @param roomId - 채팅방 ID
     * @param lastReadMessageId - 화면에 보이는 마지막 메시지 ID (이 방의 메시지여야 한다)
     * @return ChatReadResultDto - 기록된 위치와 실제로 바뀌었는지 (바뀌었을 때만 상대에게 push)
     */
    public ChatReadResultDto markRead(Long memberId, Long roomId, Long lastReadMessageId) {
        ChatRoom room = chatGetRoomUseCase.getParticipantRoom(memberId, roomId);

        if (lastReadMessageId == null || !chatMessageRepository.existsByIdAndRoomId(lastReadMessageId, roomId)) {
            throw new BusinessException(ChatErrorCode.INVALID_READ_POSITION);
        }

        boolean changed = room.markRead(memberId, lastReadMessageId);

        return new ChatReadResultDto(roomId, room.roleOf(memberId), room.lastReadIdOf(memberId), changed);
    }
}