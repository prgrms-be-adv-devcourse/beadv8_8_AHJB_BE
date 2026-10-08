package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductSyncMemberUseCase productSyncMemberUseCase;
    private final ProductCreateUseCase productCreateUseCase;

    @Transactional
    public ProductMember syncMember(MemberDto memberDto){
        return productSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public Product createProduct(Long sellerId, ProductCreateRequestDto request){
        return productCreateUseCase.createProduct(sellerId, request);
    }
}
