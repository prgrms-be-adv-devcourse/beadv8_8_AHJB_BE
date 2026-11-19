package com.jjinmak.member.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.member.domain.Member;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.domain.SocialAccount;
import com.jjinmak.member.exception.AuthErrorCode;
import com.jjinmak.member.exception.MemberErrorCode;
import com.jjinmak.member.in.dto.AddressRequestDto;
import com.jjinmak.member.in.dto.KakaoSignUpRequestDto;
import com.jjinmak.member.out.AddressRepository;
import com.jjinmak.member.out.MemberRepository;
import com.jjinmak.member.out.SocialAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberSocialSignUpUseCaseTest {

    @Mock MemberRepository memberRepository;
    @Mock AddressRepository addressRepository;
    @Mock SocialAccountRepository socialAccountRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks MemberSocialSignUpUseCase useCase;

    private final SignupTicket ticket = new SignupTicket(OAuthProvider.KAKAO, "12345", null);

    private KakaoSignUpRequestDto request() {
        return request("kakao@example.com");
    }

    private KakaoSignUpRequestDto request(String email) {
        return new KakaoSignUpRequestDto("signup-token", email, "카카오유저", "홍길동",
                "01012345678", null,
                new AddressRequestDto("집", "홍길동", "01012345678", "06236", "서울시 강남구", "101호"));
    }

    @Test
    void 추가_정보로_비밀번호_없는_회원을_만들고_카카오_계정을_연결한다() {
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(false);
        given(memberRepository.existsByEmail("kakao@example.com")).willReturn(false);
        given(memberRepository.existsByNickname("카카오유저")).willReturn(false);

        Member member = useCase.signUp(ticket, request());

        assertThat(member.hasPassword()).isFalse();
        assertThat(member.getEmail()).isEqualTo("kakao@example.com");

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("12345");

        verify(addressRepository).save(any());
        verify(eventPublisher).publishEvent(any(MemberCreatedEvent.class));
    }

    @Test
    void 이미_가입된_이메일이면_자동_연동하지_않고_거부한다() {
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(false);
        given(memberRepository.existsByEmail("kakao@example.com")).willReturn(true);

        assertThatThrownBy(() -> useCase.signUp(ticket, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);

        verify(memberRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void 이미_연결된_카카오_계정이면_거부한다() {
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(true);

        assertThatThrownBy(() -> useCase.signUp(ticket, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.ALREADY_LINKED_SOCIAL_ACCOUNT);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 닉네임이_중복되면_거부한다() {
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(false);
        given(memberRepository.existsByEmail("kakao@example.com")).willReturn(false);
        given(memberRepository.existsByNickname("카카오유저")).willReturn(true);

        assertThatThrownBy(() -> useCase.signUp(ticket, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 토큰에_인증된_이메일이_있으면_요청_이메일을_무시하고_그_이메일로_가입한다() {
        SignupTicket verified = new SignupTicket(OAuthProvider.KAKAO, "12345", "verified@kakao.com");
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(false);
        given(memberRepository.existsByEmail("verified@kakao.com")).willReturn(false);
        given(memberRepository.existsByNickname("카카오유저")).willReturn(false);

        Member member = useCase.signUp(verified, request("tampered@example.com"));

        assertThat(member.getEmail()).isEqualTo("verified@kakao.com");
        verify(memberRepository, never()).existsByEmail("tampered@example.com");
    }

    @Test
    void 토큰에_인증된_이메일이_있으면_요청_이메일이_없어도_가입된다() {
        SignupTicket verified = new SignupTicket(OAuthProvider.KAKAO, "12345", "verified@kakao.com");
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(false);
        given(memberRepository.existsByEmail("verified@kakao.com")).willReturn(false);
        given(memberRepository.existsByNickname("카카오유저")).willReturn(false);

        Member member = useCase.signUp(verified, request(null));

        assertThat(member.getEmail()).isEqualTo("verified@kakao.com");
    }

    @Test
    void 인증된_이메일이_가입된_이메일과_같으면_거부한다() {
        SignupTicket verified = new SignupTicket(OAuthProvider.KAKAO, "12345", "verified@kakao.com");
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(false);
        given(memberRepository.existsByEmail("verified@kakao.com")).willReturn(true);

        assertThatThrownBy(() -> useCase.signUp(verified, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 토큰에_이메일이_없는데_요청_이메일도_없으면_거부한다() {
        given(socialAccountRepository.existsByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345")).willReturn(false);

        assertThatThrownBy(() -> useCase.signUp(ticket, request(" ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);

        verify(memberRepository, never()).save(any());
    }
}
