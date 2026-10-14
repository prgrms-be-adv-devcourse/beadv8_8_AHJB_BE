package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.domain.RefreshToken;
import com.jjinmak.back.boundedContext.member.exception.AuthErrorCode;
import com.jjinmak.back.boundedContext.member.exception.MemberErrorCode;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.boundedContext.member.out.RefreshTokenRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberReissueTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    public Member reissue(String refreshToken){

        RefreshToken token = refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(()->new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if(token.isRevoked()){
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (token.isExpired()) {
            throw new BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(token.getMemberId())
                .orElseThrow(()->new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        if(member.isBanned()){
            throw new BusinessException(AuthErrorCode.ACCOUNT_BANNED);
        }

        token.revoke();

        return member;
    }
}
