package com.jjinmak.member.app;

import com.jjinmak.member.app.AuthResult;
import com.jjinmak.member.app.MemberReissueTokenUseCase;
import com.jjinmak.member.domain.Member;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.exception.AuthErrorCode;
import com.jjinmak.member.in.dto.KakaoSignUpRequestDto;
import com.jjinmak.member.security.SignupTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
    private final MemberReadUseCase memberReadUseCase;

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

        OAuthUserProfile profile = oAuthProviderClient.fetchUserProfile(authorizationCode);

        Optional<AuthResult> loggedIn = memberSocialLoginUseCase.login(OAuthProvider.KAKAO, profile.providerUserId());
        if (loggedIn.isPresent()) {
            return SocialLoginResult.loggedIn(loggedIn.get());
        }

        String verifiedEmail = profile.verifiedEmail();

        // 인증된 이메일로만 가입시키므로 사용자가 가입 화면에서 이메일을 바꿀 수 없다.
        // 이미 가입된 이메일이면 가입 화면을 보여 줘도 진행할 방법이 없어서, 가입 대기 토큰을 주기 전에 알린다.
        // 이메일 가입 여부는 email-availability API 로 이미 공개되어 있어 새로 노출되는 정보는 없다.
        // (가입 API 에서도 다시 검사한다: 이 응답과 가입 요청 사이에 같은 이메일로 가입될 수 있기 때문)
        if (verifiedEmail != null && !memberReadUseCase.isEmailAvailable(verifiedEmail)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        String signupToken = signupTokenProvider.create(
                new SignupTicket(OAuthProvider.KAKAO, profile.providerUserId(), verifiedEmail));

        // 닉네임은 사용자가 가입 화면에서 수정할 수 있는 제안값이라 토큰에 담지 않는다.
        // 가입 API 는 요청 바디의 nickname 을 쓰고, 중복·길이 검증도 그쪽에서 한다.
        return SocialLoginResult.signupRequired(signupToken, verifiedEmail, profile.nickname());
    }

    @Transactional
    public AuthResult signUpWithKakao(KakaoSignUpRequestDto request) {

        SignupTicket ticket = signupTokenProvider.parse(request.signupToken());

        Member member = memberSocialSignUpUseCase.signUp(ticket, request);

        TokenPair pair = memberTokenIssueUseCase.issue(member);

        return new AuthResult(pair.accessToken(), pair.refreshToken(), member.isSuspended());
    }
}
