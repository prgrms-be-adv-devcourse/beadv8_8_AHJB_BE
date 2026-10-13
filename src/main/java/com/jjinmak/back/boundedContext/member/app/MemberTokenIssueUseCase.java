package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.domain.RefreshToken;
import com.jjinmak.back.boundedContext.member.out.RefreshTokenRepository;
import com.jjinmak.back.global.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MemberTokenIssueUseCase {

    private final long refreshTokenExpiration;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public MemberTokenIssueUseCase(
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository
    ){
        this.refreshTokenExpiration = refreshTokenExpiration ;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public TokenPair issue(Member member){
        List<String> authorities = member.getAuthorities();

        String refreshTokenValue = UUID.randomUUID().toString();

        LocalDateTime expiredAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration/1000);

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), authorities);

        RefreshToken token = RefreshToken.create(member.getId(), refreshTokenValue, expiredAt);

        refreshTokenRepository.save(token);

        return new TokenPair(accessToken, refreshTokenValue);
    }
}
