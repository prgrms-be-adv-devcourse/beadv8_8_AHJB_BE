package com.jjinmak.member.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.domain.Member;
import com.jjinmak.member.exception.AuthErrorCode;
import com.jjinmak.member.out.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberAuthenticateUseCaseSocialTest {

    @Mock MemberRepository memberRepository;
    @Mock MemberLoginFailHandler loginFailHandler;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks MemberAuthenticateUseCase useCase;

    @Test
    void 소셜_회원은_비밀번호_로그인이_거부되고_실패_횟수가_오르지_않는다() {
        Member member = Member.createSocial("kakao@example.com", "카카오유저", "홍길동", "01012345678", null);
        given(memberRepository.findByEmail("kakao@example.com")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> useCase.authenticate("kakao@example.com", "guess"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);

        verify(loginFailHandler, never()).handleFail(any());
        assertThat(member.isLocked()).isFalse();
    }
}
