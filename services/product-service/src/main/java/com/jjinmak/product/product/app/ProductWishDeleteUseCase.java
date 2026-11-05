package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.domain.ProductWish;
import com.jjinmak.product.product.in.dto.ProductWishResponseDto;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.product.product.out.ProductWishRepository;
import com.jjinmak.common.exception.BusinessException;
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
