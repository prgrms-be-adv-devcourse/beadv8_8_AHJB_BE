package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.domain.MemberSanction;
import com.jjinmak.back.boundedContext.member.exception.AuthErrorCode;
import com.jjinmak.back.boundedContext.member.out.MemberSanctionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberSanctionReleaseUseCase {

    private final MemberSanctionRepository memberSanctionRepository;

    public void releaseIfExpired(Member member){
        if(!member.isSuspended()){
            return;
        }

        MemberSanction sanction = memberSanctionRepository.findActiveByMemberId(member.getId())
                .orElseThrow(()-> new BusinessException(AuthErrorCode.SANCTION_NOT_FOUND));

        if( sanction.isExpired()){
            sanction.release();
            member.active();
        }
    }
}
