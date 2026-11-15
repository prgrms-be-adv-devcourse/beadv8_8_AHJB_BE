package com.jjinmak.chat.in;

import com.jjinmak.chat.app.ChatFacade;
import com.jjinmak.chat.in.dto.ChatMessageListDto;
import com.jjinmak.chat.in.dto.ChatRoomDetailDto;
import com.jjinmak.chat.in.dto.ChatRoomListDto;
import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-rooms")
public class ChatApiController {

    private final ChatFacade chatFacade;

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
}