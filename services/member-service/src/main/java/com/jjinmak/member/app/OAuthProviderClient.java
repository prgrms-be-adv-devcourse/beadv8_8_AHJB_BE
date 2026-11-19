package com.jjinmak.member.app;

/**
 * 외부 OAuth 제공자에게서 회원을 식별하는 포트.
 * 유스케이스가 특정 제공자의 HTTP API 에 직접 묶이지 않게 하고, 테스트에서 가짜 구현으로 바꿀 수 있게 한다.
 */
public interface OAuthProviderClient {

    /**
     * 인가 코드를 제공자 토큰으로 교환한 뒤, 제공자가 발급한 회원 식별자와 인증된 이메일을 반환한다.
     * 제공자 토큰은 조회에만 쓰고 보관하지 않는다.
     */
    OAuthUserProfile fetchUserProfile(String authorizationCode);
}
