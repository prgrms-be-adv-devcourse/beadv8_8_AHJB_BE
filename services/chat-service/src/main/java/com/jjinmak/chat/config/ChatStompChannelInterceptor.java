package com.jjinmak.chat.config;

import com.jjinmak.chat.app.ChatFacade;
import com.jjinmak.chat.exception.ChatErrorCode;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

// 클라이언트 → 서버로 들어오는 STOMP 프레임을 가로챈다. SUBSCRIBE일 때 그 방의 참여자인지 확인한다.
@Component
@RequiredArgsConstructor
public class ChatStompChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_TOPIC_PREFIX = "/topic/chat-rooms/";

    private final ChatFacade chatFacade;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && isRoomTopic(accessor.getDestination())) {
            Long memberId = memberIdOf(accessor);
            Long roomId = roomIdOf(accessor.getDestination());
            chatFacade.checkParticipant(memberId, roomId);
        }

        return message;
    }

    private boolean isRoomTopic(String destination) {
        return destination != null && destination.startsWith(ROOM_TOPIC_PREFIX);
    }

    private Long memberIdOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal principal) {
            return principal.memberId();
        }
        throw new BusinessException(ChatErrorCode.NOT_PARTICIPANT);
    }

    private Long roomIdOf(String destination) {
        try {
            return Long.valueOf(destination.substring(ROOM_TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ChatErrorCode.ROOM_NOT_FOUND);
        }
    }
}