package com.jjinmak.member.app;

/**
 * 외부 제공자에게서 확인한 사용자 정보.
 *
 * @param providerUserId 제공자가 발급한 회원 식별자 (항상 존재)
 * @param verifiedEmail  제공자가 유효·인증을 보장한 이메일. 동의하지 않았거나 미인증·만료 상태면 null
 * @param nickname       제공자 닉네임. 가입 화면을 미리 채우는 제안값일 뿐 검증되지 않은 값이다. 없으면 null
 */
public record OAuthUserProfile(
        String providerUserId,
        String verifiedEmail,
        String nickname
) {
}
