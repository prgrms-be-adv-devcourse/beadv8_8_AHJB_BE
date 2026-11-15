package com.jjinmak.chat.app;

import com.jjinmak.chat.domain.ChatMember;
import com.jjinmak.chat.domain.ChatRoom;
import com.jjinmak.chat.exception.ChatErrorCode;
import com.jjinmak.chat.in.dto.ChatRoomDetailDto;
import com.jjinmak.chat.out.ChatMemberRepository;
import com.jjinmak.chat.out.ChatRoomRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatGetRoomUseCase {

    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;

    /**
     * 채팅방 하나를 조회한다. 그 방의 구매자 또는 판매자만 볼 수 있다.
     *
     * @param memberId - 조회하는 회원 ID (로그인 회원)
     * @param roomId - 채팅방 ID
     * @return ChatRoomDetailDto - 채팅방 상세 (상대 닉네임, 내 역할 포함)
     */
    public ChatRoomDetailDto getRoom(Long memberId, Long roomId) {
        ChatRoom room = getParticipantRoom(memberId, roomId);

        String counterpartNickname = chatMemberRepository.findById(room.counterpartId(memberId))
                .map(ChatMember::getNickname)
                .orElse(UNKNOWN_NICKNAME);

        return room.detailDto(memberId, counterpartNickname);
    }

    /**
     * 채팅방을 찾고 참여자인지 확인한다. 메시지 조회·전송 UseCase도 같은 검증을 쓴다.
     *
     * @param memberId - 접근하는 회원 ID
     * @param roomId - 채팅방 ID
     * @return ChatRoom - 참여자 검증을 통과한 채팅방
     */
    public ChatRoom getParticipantRoom(Long memberId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.ROOM_NOT_FOUND));

        if (!room.isParticipant(memberId)) {
            throw new BusinessException(ChatErrorCode.NOT_PARTICIPANT);
        }

        return room;
    }
}