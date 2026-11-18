package com.jjinmak.member.domain;

import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 제공자(카카오 등) 계정과 회원의 연결.
 * 제공자 쪽 식별자는 이메일이 아니라 제공자가 발급한 회원번호다(이메일은 동의 여부에 따라 없을 수 있고 바뀔 수 있다).
 */
@Entity
@Getter
@Table(name = "social_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_account_provider_user", columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "uk_social_account_member_provider", columnNames = {"member_id", "provider"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseIdAndTime {

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(nullable = false, length = 100)
    private String providerUserId;

    private SocialAccount(Long memberId, OAuthProvider provider, String providerUserId) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static SocialAccount link(Long memberId, OAuthProvider provider, String providerUserId) {
        return new SocialAccount(memberId, provider, providerUserId);
    }
}
