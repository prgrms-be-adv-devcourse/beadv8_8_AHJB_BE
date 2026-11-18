package com.jjinmak.boundedcontext.chat.app;

import com.jjinmak.boundedcontext.chat.domain.ChatMember;
import com.jjinmak.boundedcontext.chat.domain.ChatRoom;
import com.jjinmak.boundedcontext.chat.in.dto.ChatRoomItemDto;
import com.jjinmak.boundedcontext.chat.out.ChatMemberRepository;
import com.jjinmak.boundedcontext.chat.out.ChatMessageRepository;
import com.jjinmak.boundedcontext.chat.out.ChatRoomRepository;
import com.jjinmak.boundedcontext.chat.out.ChatUnreadCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatGetRoomsUseCase {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 로그인한 회원이 참여한(구매자 또는 판매자) 거래 채팅방 목록을 최근 대화순으로 조회한다.
     * 상대 닉네임은 회원 복제본(ChatMember)에서, 안 읽은 수는 집계 쿼리로 각각 한 번에 읽는다 (방마다 조회하지 않음).
     *
     * @param memberId - 조회하는 회원 ID (로그인 회원)
     * @param page - 페이지 번호 (0부터)
     * @param size - 페이지 크기 (1~50)
     * @return Page<ChatRoomItemDto> - 채팅방 목록 페이지 (마지막 메시지 미리보기, 안 읽은 수 포함)
     */
    public Page<ChatRoomItemDto> getRooms(Long memberId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));

        Page<ChatRoom> rooms = chatRoomRepository.findByParticipant(memberId, pageable);

        List<Long> counterpartIds = rooms.getContent().stream()
                .map(room -> room.counterpartId(memberId))
                .distinct()
                .toList();

        Map<Long, String> nicknames = chatMemberRepository.findAllById(counterpartIds).stream()
                .collect(Collectors.toMap(ChatMember::getId, ChatMember::getNickname));

        List<Long> roomIds = rooms.getContent().stream()
                .map(ChatRoom::getId)
                .toList();

        Map<Long, Long> unreadCounts = roomIds.isEmpty()
                ? Map.of()
                : chatMessageRepository.countUnreadByRooms(memberId, roomIds).stream()
                .collect(Collectors.toMap(ChatUnreadCount::roomId, ChatUnreadCount::count));

        return rooms.map(room -> room.itemDto(
                memberId,
                nicknames.getOrDefault(room.counterpartId(memberId), UNKNOWN_NICKNAME),
                unreadCounts.getOrDefault(room.getId(), 0L)
        ));
    }
}