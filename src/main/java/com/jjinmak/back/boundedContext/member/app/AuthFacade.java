package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.domain.Member;
import com.jjinmak.back.boundedContext.member.in.dto.MemberLoginResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthFacade {

    private final MemberAuthenticateUseCase memberAuthenticateUseCase;
    private final MemberTokenIssueUseCase memberTokenIssueUseCase;

    public MemberLoginResponseDto login(String email, String password){

        Member member = memberAuthenticateUseCase.authenticate(email, password);
        TokenPair pair = memberTokenIssueUseCase.issue(member);

        return new MemberLoginResponseDto(pair.accessToken(), pair.refreshToken(), member.getNickname(), member.isSuspended());
    }

}
