package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.RefreshToken;
import com.jjinmak.back.boundedContext.member.out.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberLogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void revokeRefreshToken(String refreshToken){
//      사용자에게 등록된 토큰이 없다 라는 정보를 제공하지 않음
        refreshTokenRepository.findByRefreshToken(refreshToken)
                .ifPresent(RefreshToken::revoke);
    }
}