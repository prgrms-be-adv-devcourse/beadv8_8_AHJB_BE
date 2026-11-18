package com.jjinmak.member.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.domain.Member;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.domain.SocialAccount;
import com.jjinmak.member.exception.MemberErrorCode;
import com.jjinmak.member.out.MemberRepository;
import com.jjinmak.member.out.SocialAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class MemberSocialLoginUseCaseTest {

    @Mock SocialAccountRepository socialAccountRepository;
    @Mock MemberRepository memberRepository;
    @Mock MemberSanctionReleaseUseCase memberSanctionReleaseUseCase;
    @Mock MemberTokenIssueUseCase memberTokenIssueUseCase;

    @InjectMocks MemberSocialLoginUseCase useCase;

    private Member member() {
        return Member.createSocial("kakao@example.com", "카카오유저", "홍길동", "01012345678", null);
    }

    @Test
    void 연결된_회원이_없으면_빈_값을_반환한다() {
        given(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.empty());

        assertThat(useCase.login(OAuthProvider.KAKAO, "12345")).isEmpty();
        verify(memberTokenIssueUseCase, never()).issue(any());
    }

    @Test
    void 연결된_회원이면_토큰을_발급한다() {
        Member member = member();
        given(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.of(SocialAccount.link(1L, OAuthProvider.KAKAO, "12345")));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberTokenIssueUseCase.issue(member)).willReturn(new TokenPair("access", "refresh"));

        Optional<AuthResult> result = useCase.login(OAuthProvider.KAKAO, "12345");

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("access");
        assertThat(result.get().refreshToken()).isEqualTo("refresh");
        verify(memberSanctionReleaseUseCase).releaseIfExpired(member);
    }

    @Test
    void 차단된_회원은_토큰을_발급하지_않는다() {
        Member member = member();
        member.ban();
        given(socialAccountRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.of(SocialAccount.link(1L, OAuthProvider.KAKAO, "12345")));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> useCase.login(OAuthProvider.KAKAO, "12345"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.BANNED_ACCOUNT);

        verify(memberTokenIssueUseCase, never()).issue(any());
    }
}
