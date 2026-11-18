package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.out.ProductMemberRepository;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductSyncMemberUseCase {

    private final ProductMemberRepository productMemberRepository;

    public ProductMember syncMember(MemberDto memberDto){
        Long id = memberDto.id();
        String nickname = memberDto.nickname();

        ProductMember member = productMemberRepository.findById(id)
                .orElseGet(() -> new ProductMember(id, nickname));

        member.sync(nickname);

        return productMemberRepository.save(member);
    }
}
