package com.jjinmak.chat.in;

import com.jjinmak.chat.app.ChatFacade;
import com.jjinmak.chat.config.ChatPrincipal;
import com.jjinmak.chat.in.dto.ChatMessagePushDto;
import com.jjinmak.chat.in.dto.ChatMessageSendDto;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.ErrorCode;
import com.jjinmak.common.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatFacade chatFacade;
    private final SimpMessagingTemplate messagingTemplate;

    // 클라이언트 SEND /app/chat-rooms/{roomId}/messages → 저장 → /topic/chat-rooms/{roomId} 구독자에게 전달
    @MessageMapping("/chat-rooms/{roomId}/messages")
    public void sendMessage(@DestinationVariable Long roomId, ChatMessageSendDto request, Principal principal) {
        Long memberId = ((ChatPrincipal) principal).memberId();

        ChatMessagePushDto pushed = chatFacade.sendMessage(memberId, roomId, request.content());

        messagingTemplate.convertAndSend("/topic/chat-rooms/" + roomId, pushed);
    }

    // 전송 중 BusinessException은 보낸 사람에게만 /user/queue/errors 로 알린다 (연결은 유지)
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleBusinessException(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return new ErrorResponse(false, code.getCode(), code.getMessage());
    }
}