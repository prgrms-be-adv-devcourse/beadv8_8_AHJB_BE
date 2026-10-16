package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.out.MemberRepository;
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
}