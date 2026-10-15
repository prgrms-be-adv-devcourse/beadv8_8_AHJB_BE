package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductWish;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.boundedContext.product.out.ProductWishRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductWishDeleteUseCase {

    private final ProductRepository productRepository;
    private final ProductWishRepository productWishRepository;

    public ProductWishResponseDto unwish(Long memberId, Long productId){
        ProductWish wish = productWishRepository.findByMemberIdAndProductId(memberId, productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.WISH_NOT_FOUND));

        productWishRepository.delete(wish);

        productRepository.decreaseWishCount(productId);

        return new ProductWishResponseDto(productId, false, productRepository.findWishCount(productId));
    }
}
