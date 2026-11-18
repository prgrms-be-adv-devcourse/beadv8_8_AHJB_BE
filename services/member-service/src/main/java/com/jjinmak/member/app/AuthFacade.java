package com.jjinmak.member.app;

import com.jjinmak.member.app.AuthResult;
import com.jjinmak.member.app.MemberReissueTokenUseCase;
import com.jjinmak.member.domain.Member;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.in.dto.KakaoSignUpRequestDto;
import com.jjinmak.member.security.SignupTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final MemberAuthenticateUseCase memberAuthenticateUseCase;
    private final MemberTokenIssueUseCase memberTokenIssueUseCase;
    private final MemberReissueTokenUseCase memberReissueTokenUseCase;
    private final MemberSanctionReleaseUseCase memberSanctionReleaseUseCase;
    private final OAuthProviderClient oAuthProviderClient;
    private final MemberSocialLoginUseCase memberSocialLoginUseCase;
    private final MemberSocialSignUpUseCase memberSocialSignUpUseCase;
    private final SignupTokenProvider signupTokenProvider;

    @Transactional
    public AuthResult login(String email, String password){

        Member member = memberAuthenticateUseCase.authenticate(email, password);

        memberSanctionReleaseUseCase.releaseIfExpired(member);

        TokenPair pair = memberTokenIssueUseCase.issue(member);

        return new AuthResult(pair.accessToken(), pair.refreshToken(), member.isSuspended());
    }

    @Transactional
    public AuthResult reissue(String refreshToken){

        Member member = memberReissueTokenUseCase.reissue(refreshToken);

        memberSanctionReleaseUseCase.releaseIfExpired(member);

        TokenPair pair = memberTokenIssueUseCase.issue(member);

        return new AuthResult(pair.accessToken(), pair.refreshToken(), member.isSuspended());
    }

    /**
     * 의도적으로 트랜잭션을 걸지 않는다.
     * 카카오 API 응답을 기다리는 동안 DB 커넥션(풀 5개)을 잡고 있으면 다른 요청이 커넥션을 얻지 못한다.
     * 외부 호출이 끝난 뒤 DB 작업만 MemberSocialLoginUseCase 의 트랜잭션에서 처리한다.
     */
    public SocialLoginResult loginWithKakao(String authorizationCode) {

        String providerUserId = oAuthProviderClient.fetchProviderUserId(authorizationCode);

        return memberSocialLoginUseCase.login(OAuthProvider.KAKAO, providerUserId)
                .map(SocialLoginResult::loggedIn)
                .orElseGet(() -> SocialLoginResult.signupRequired(
                        signupTokenProvider.create(new SignupTicket(OAuthProvider.KAKAO, providerUserId))));
    }

    @Transactional
    public AuthResult signUpWithKakao(KakaoSignUpRequestDto request) {

        SignupTicket ticket = signupTokenProvider.parse(request.signupToken());

        Member member = memberSocialSignUpUseCase.signUp(ticket, request);

        TokenPair pair = memberTokenIssueUseCase.issue(member);

        return new AuthResult(pair.accessToken(), pair.refreshToken(), member.isSuspended());
    }
}
