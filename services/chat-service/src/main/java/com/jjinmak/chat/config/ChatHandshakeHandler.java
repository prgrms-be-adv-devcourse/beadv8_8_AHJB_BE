package com.jjinmak.chat.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

// 핸드셰이크(HTTP Upgrade) 요청의 X-Member-Id를 WebSocket 세션의 Principal로 만든다.
// 이 요청은 SecurityConfig를 거치므로 헤더가 없으면 여기까지 오기 전에 401이다.
public class ChatHandshakeHandler extends DefaultHandshakeHandler {

    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String memberId = request.getHeaders().getFirst(MEMBER_ID_HEADER);
        if (memberId == null) {
            return null;
        }
        return new ChatPrincipal(Long.valueOf(memberId));
    }
}