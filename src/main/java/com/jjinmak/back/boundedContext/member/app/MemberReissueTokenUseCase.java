package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.domain.RefreshToken;
import com.jjinmak.back.boundedContext.member.exception.AuthErrorCode;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.boundedContext.member.out.RefreshTokenRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberReissueTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Member reissue(String refreshToken){

        RefreshToken token = refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(()->new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        token.consume(LocalDateTime.now());

        Member member = memberRepository.findById(token.getMemberId())
                .orElseThrow(()->new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        member.validateNotBanned();

        return member;
    }
}