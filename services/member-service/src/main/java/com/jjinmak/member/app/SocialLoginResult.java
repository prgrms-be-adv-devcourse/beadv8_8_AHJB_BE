package com.jjinmak.member.app;

/**
 * 소셜 로그인 결과.
 * 연결된 회원이 있으면 authResult, 처음 로그인한 사용자면 가입 대기 토큰(signupToken)을 가진다.
 */
public record SocialLoginResult(
        AuthResult authResult,
        String signupToken
) {
    public static SocialLoginResult loggedIn(AuthResult authResult) {
        return new SocialLoginResult(authResult, null);
    }

    public static SocialLoginResult signupRequired(String signupToken) {
        return new SocialLoginResult(null, signupToken);
    }

    public boolean isSignupRequired() {
        return signupToken != null;
    }
}
