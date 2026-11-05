package com.jjinmak.member.app;

import com.jjinmak.member.domain.Member;
import com.jjinmak.member.domain.RefreshToken;
import com.jjinmak.member.exception.AuthErrorCode;
import com.jjinmak.member.out.MemberRepository;
import com.jjinmak.member.out.RefreshTokenRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
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
