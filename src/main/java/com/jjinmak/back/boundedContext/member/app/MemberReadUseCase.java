package com.jjinmak.back.boundedContext.member.app;

import com.jjinmak.back.boundedContext.member.out.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberReadUseCase {

    private final MemberRepository memberRepository;

    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(email);
    }

    public boolean isNicknameAvailable(String nickname){
        return !memberRepository.existsByNickname(nickname);
    }
}
