package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.in.dto.MemberSummaryResponseDto;
import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberReadUseCase {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname){
        return !memberRepository.existsByNickname(nickname);
    }

    @Transactional(readOnly = true)
    public MemberSummaryResponseDto getSummary(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        return new MemberSummaryResponseDto(member.getNickname(), member.isSeller(), member.isSuspended());
    }
}
