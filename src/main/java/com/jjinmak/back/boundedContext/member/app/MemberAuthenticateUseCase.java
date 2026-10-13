package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.exception.AuthErrorCode;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberAuthenticateUseCase {

    private final MemberRepository memberRepository;
    private final MemberLoginFailHandler loginFailHandler;
    private final PasswordEncoder passwordEncoder;

    public Member authenticate(String email, String password){

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()-> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        member.unlockIfExpired();

        if(member.isBanned()){
            throw new BusinessException(AuthErrorCode.ACCOUNT_BANNED);
        }

        if(member.isLocked()){
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }

        if(!member.validPassword(passwordEncoder, password)){
            loginFailHandler.handleFail(member.getId());
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        member.resetLoginFailCount();

        return member;
    }
}
