package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.in.dto.ProductWishListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductWishFacade {

    private final ProductWishCreateUseCase productWishCreateUseCase;
    private final ProductWishDeleteUseCase productWishDeleteUseCase;
    private final ProductWishReadUseCase productWishReadUseCase;

    @Transactional
    public ProductWishResponseDto wish(Long memberId, Long productId){
        return productWishCreateUseCase.wish(memberId, productId);
    }

    @Transactional
    public ProductWishResponseDto unwish(Long memberId, Long productId){
        return productWishDeleteUseCase.unwish(memberId, productId);
    }

    @Transactional(readOnly = true)
    public ProductWishListResponseDto readMyWishes(Long memberId, int page, int size){
        return productWishReadUseCase.readMyWishes(memberId, page, size);
    }
}
