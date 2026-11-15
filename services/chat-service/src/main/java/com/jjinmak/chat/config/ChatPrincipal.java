package com.jjinmak.chat.config;

import java.security.Principal;

/**
 * WebSocket 세션에 붙는 회원 식별자. 핸드셰이크 때 X-Member-Id 헤더에서 만들어 이후 모든 STOMP 프레임에서 꺼내 쓴다.
 */
public record ChatPrincipal(Long memberId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}