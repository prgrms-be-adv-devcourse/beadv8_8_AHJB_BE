package com.jjinmak.member.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.exception.AuthErrorCode;
import com.jjinmak.member.security.SignupTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthFacadeKakaoLoginTest {

    @Mock MemberAuthenticateUseCase memberAuthenticateUseCase;
    @Mock MemberTokenIssueUseCase memberTokenIssueUseCase;
    @Mock MemberReissueTokenUseCase memberReissueTokenUseCase;
    @Mock MemberSanctionReleaseUseCase memberSanctionReleaseUseCase;
    @Mock OAuthProviderClient oAuthProviderClient;
    @Mock MemberSocialLoginUseCase memberSocialLoginUseCase;
    @Mock MemberSocialSignUpUseCase memberSocialSignUpUseCase;
    @Mock SignupTokenProvider signupTokenProvider;
    @Mock MemberReadUseCase memberReadUseCase;

    @InjectMocks AuthFacade authFacade;

    @Test
    void 연결된_회원이면_로그인_결과를_반환하고_이메일_중복은_검사하지_않는다() {
        given(oAuthProviderClient.fetchUserProfile("code"))
                .willReturn(new OAuthUserProfile("12345", "user@kakao.com", "카카오닉네임"));
        AuthResult auth = new AuthResult("access", "refresh", false);
        given(memberSocialLoginUseCase.login(OAuthProvider.KAKAO, "12345")).willReturn(Optional.of(auth));

        SocialLoginResult result = authFacade.loginWithKakao("code");

        assertThat(result.isSignupRequired()).isFalse();
        assertThat(result.authResult()).isEqualTo(auth);
        verify(memberReadUseCase, never()).isEmailAvailable(any());
    }

    @Test
    void 처음_로그인하면_인증된_이메일을_담은_가입_대기_토큰을_발급한다() {
        given(oAuthProviderClient.fetchUserProfile("code"))
                .willReturn(new OAuthUserProfile("12345", "user@kakao.com", "카카오닉네임"));
        given(memberSocialLoginUseCase.login(OAuthProvider.KAKAO, "12345")).willReturn(Optional.empty());
        given(memberReadUseCase.isEmailAvailable("user@kakao.com")).willReturn(true);
        given(signupTokenProvider.create(any())).willReturn("signup-token");

        SocialLoginResult result = authFacade.loginWithKakao("code");

        assertThat(result.isSignupRequired()).isTrue();
        assertThat(result.signupToken()).isEqualTo("signup-token");
        assertThat(result.email()).isEqualTo("user@kakao.com");
        assertThat(result.nickname()).isEqualTo("카카오닉네임");

        ArgumentCaptor<SignupTicket> captor = ArgumentCaptor.forClass(SignupTicket.class);
        verify(signupTokenProvider).create(captor.capture());
        assertThat(captor.getValue().verifiedEmail()).isEqualTo("user@kakao.com");
    }

    @Test
    void 인증된_이메일이_이미_가입되어_있으면_가입_대기_토큰_없이_거부한다() {
        given(oAuthProviderClient.fetchUserProfile("code"))
                .willReturn(new OAuthUserProfile("12345", "user@kakao.com", "카카오닉네임"));
        given(memberSocialLoginUseCase.login(OAuthProvider.KAKAO, "12345")).willReturn(Optional.empty());
        given(memberReadUseCase.isEmailAvailable("user@kakao.com")).willReturn(false);

        assertThatThrownBy(() -> authFacade.loginWithKakao("code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.EMAIL_ALREADY_REGISTERED);

        verify(signupTokenProvider, never()).create(any());
    }

    @Test
    void 인증된_이메일이_없으면_이메일_없이_가입_대기_토큰을_발급한다() {
        given(oAuthProviderClient.fetchUserProfile("code"))
                .willReturn(new OAuthUserProfile("12345", null, null));
        given(memberSocialLoginUseCase.login(OAuthProvider.KAKAO, "12345")).willReturn(Optional.empty());
        given(signupTokenProvider.create(any())).willReturn("signup-token");

        SocialLoginResult result = authFacade.loginWithKakao("code");

        assertThat(result.isSignupRequired()).isTrue();
        assertThat(result.email()).isNull();
        assertThat(result.nickname()).isNull();
        verify(memberReadUseCase, never()).isEmailAvailable(any());
    }
}
