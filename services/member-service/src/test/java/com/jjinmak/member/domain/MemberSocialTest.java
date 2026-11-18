package com.jjinmak.member.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.exception.MemberErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberSocialTest {

    private Member socialMember() {
        return Member.createSocial("kakao@example.com", "카카오유저", "홍길동", "01012345678", null);
    }

    @Test
    void 소셜_회원은_비밀번호가_없다() {
        Member member = socialMember();

        assertThat(member.hasPassword()).isFalse();
        assertThat(member.getUuid()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void 소셜_회원은_어떤_비밀번호로도_일치하지_않는다() {
        Member member = socialMember();

        assertThat(member.matchesPassword(new BCryptPasswordEncoder(), "anything")).isFalse();
        assertThat(member.matchesPassword(new BCryptPasswordEncoder(), "")).isFalse();
    }

    @Test
    void 차단된_회원은_소셜_로그인할_수_없다() {
        Member member = socialMember();
        member.ban();

        assertThatThrownBy(member::prepareSocialLogin)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.BANNED_ACCOUNT);
    }

    @Test
    void 정지된_회원은_소셜_로그인_자체는_가능하다() {
        Member member = socialMember();
        member.suspend();

        member.prepareSocialLogin();

        assertThat(member.isSuspended()).isTrue();
    }
}
