package com.jjinmak.member.app;

import com.jjinmak.member.domain.Member;
import com.jjinmak.member.domain.OAuthProvider;
import com.jjinmak.member.out.MemberRepository;
import com.jjinmak.member.out.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberSocialLoginUseCase {

    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final MemberSanctionReleaseUseCase memberSanctionReleaseUseCase;
    private final MemberTokenIssueUseCase memberTokenIssueUseCase;

    /**
     * 외부 계정에 연결된 회원으로 로그인한다. 연결된 회원이 없으면 빈 값을 반환한다(가입 필요).
     * 차단 확인 → 기간 지난 정지 해제 → 토큰 발급 순서는 일반 로그인(AuthFacade.login)과 같다.
     */
    @Transactional
    public Optional<AuthResult> login(OAuthProvider provider, String providerUserId) {
        return socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(account -> {
                    Member member = memberRepository.findById(account.getMemberId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "소셜 계정에 연결된 회원이 없습니다. socialAccountId=" + account.getId()));

                    member.prepareSocialLogin();

                    memberSanctionReleaseUseCase.releaseIfExpired(member);

                    TokenPair pair = memberTokenIssueUseCase.issue(member);

                    return new AuthResult(pair.accessToken(), pair.refreshToken(), member.isSuspended());
                });
    }
}
