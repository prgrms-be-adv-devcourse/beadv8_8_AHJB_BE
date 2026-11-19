package com.jjinmak.member.app;

import com.jjinmak.member.domain.OAuthProvider;

/**
 * 가입 대기 토큰에서 꺼낸, 서버가 확인한 외부 계정 정보.
 *
 * @param verifiedEmail 제공자가 인증한 이메일. 없으면 null 이며, 이때는 사용자가 입력한 이메일로 가입한다
 */
public record SignupTicket(
        OAuthProvider provider,
        String providerUserId,
        String verifiedEmail
) {
    public boolean hasVerifiedEmail() {
        return verifiedEmail != null;
    }
}
