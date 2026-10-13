package com.jjinmak.back.boundedContext.review.app;

import com.jjinmak.back.boundedContext.review.domain.ReviewMember;
import com.jjinmak.back.boundedContext.review.out.ReviewMemberRepository;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewSyncMemberUseCase {

    private final ReviewMemberRepository reviewMemberRepository;

    public ReviewMember syncMember(MemberDto memberDto){
        Long id = memberDto.id();
        String nickname = memberDto.nickname();

        ReviewMember member = reviewMemberRepository.findById(id)
                .orElseGet(() -> new ReviewMember(id, nickname));

        member.sync(nickname);

        return reviewMemberRepository.save(member);
    }
}
