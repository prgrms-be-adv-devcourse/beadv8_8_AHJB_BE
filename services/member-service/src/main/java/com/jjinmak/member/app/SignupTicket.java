package com.jjinmak.member.app;

import com.jjinmak.member.domain.OAuthProvider;

/** 가입 대기 토큰에서 꺼낸, 서버가 확인한 외부 계정 정보 */
public record SignupTicket(
        OAuthProvider provider,
        String providerUserId
) {
}
