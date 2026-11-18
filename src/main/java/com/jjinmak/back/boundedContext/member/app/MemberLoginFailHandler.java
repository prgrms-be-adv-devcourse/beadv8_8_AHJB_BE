package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberLoginFailHandler {

    private final MemberRepository memberRepository;

//  인증중 예외처리 시 Transaction Rollback으로 인해 LoginFailCount가 증가되지 않는 상황을 고려하여 별도 Transaction으로 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFail(Long memberId){
        Member member = memberRepository.findById(memberId)
                .orElseThrow(()-> new BusinessException(CommonErrorCode.USER_NOT_FOUND));
        member.increaseLoginFailCount(LocalDateTime.now());
    }

}
