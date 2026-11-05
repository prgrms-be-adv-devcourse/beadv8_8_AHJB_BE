package com.jjinmak.product.review.app;

import com.jjinmak.product.review.domain.ReviewMember;
import com.jjinmak.product.review.out.ReviewMemberRepository;
import com.jjinmak.contracts.member.dto.MemberDto;
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
