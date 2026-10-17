package com.jjinmak.back.boundedContext.member.domain;

import com.jjinmak.back.boundedContext.member.exception.AuthErrorCode;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "refresh_token", indexes = @Index(
        name = "idx_refresh_token_member_id", columnList = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseIdAndTime {

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 500, unique = true)
    private String refreshToken;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(nullable = false)
    private boolean revoked;

    private RefreshToken(Long memberId, String refreshToken, LocalDateTime expiredAt){
        this.memberId = memberId;
        this.refreshToken = refreshToken;
        this.expiredAt = expiredAt;
        this.revoked = false;
    }

    public static RefreshToken create(Long memberId, String refreshToken, LocalDateTime expiredAt){
        return new RefreshToken(memberId, refreshToken, expiredAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiredAt.isBefore(now);
    }

    // 한 번 사용한 토큰은 무효화
    public void consume(LocalDateTime now) {
        if (isRevoked()) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (isExpired(now)) {
            throw new BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }
        revoke();
    }

    public void revoke(){
        revoked = true;
    }
}
