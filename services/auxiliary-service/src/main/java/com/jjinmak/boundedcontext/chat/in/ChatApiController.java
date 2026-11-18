package com.jjinmak.boundedcontext.chat.in;

import com.jjinmak.boundedcontext.chat.app.ChatFacade;
import com.jjinmak.boundedcontext.chat.in.dto.*;
import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-rooms")
public class ChatApiController {

    private final ChatFacade chatFacade;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<RsData<ChatRoomListDto>> getRooms(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ChatRoomListDto response = chatFacade.getRooms(principal.memberId(), page, size);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RsData<ChatRoomDetailDto>> getRoom(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long roomId
    ) {
        ChatRoomDetailDto response = chatFacade.getRoom(principal.memberId(), roomId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<RsData<ChatMessageListDto>> getMessages(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        ChatMessageListDto response = chatFacade.getMessages(principal.memberId(), roomId, page, size);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @PatchMapping("/{roomId}/read")
    public ResponseEntity<RsData<ChatReadResponseDto>> markRead(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long roomId,
            @RequestBody ChatReadRequestDto request
    ) {
        ChatReadResultDto result = chatFacade.markRead(principal.memberId(), roomId, request.lastReadMessageId());

        if (result.changed()) {
            messagingTemplate.convertAndSend("/topic/chat-rooms/" + roomId, ChatReadPushDto.from(result));
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body(new RsData<>(new ChatReadResponseDto(result.roomId(), result.lastReadMessageId())));
    }
}