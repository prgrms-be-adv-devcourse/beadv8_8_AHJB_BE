package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.domain.ProductWish;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductMemberRepository;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.boundedContext.product.out.ProductWishRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductWishCreateUseCase {

    private final ProductRepository productRepository;
    private final ProductMemberRepository productMemberRepository;
    private final ProductWishRepository productWishRepository;

    public ProductWishResponseDto wish(Long memberId, Long productId){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        ProductMember member = productMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        if (productWishRepository.existsByMemberIdAndProductId(memberId, productId)){
            throw new BusinessException(ProductErrorCode.ALREADY_WISHED);
        }

        try {
            productWishRepository.saveAndFlush(ProductWish.create(product, member));
        } catch (DataIntegrityViolationException e) {
            // 같은 요청이 동시에 들어와 위 검사를 함께 통과한 경우, 유니크 제약이 막는다.
            throw new BusinessException(ProductErrorCode.ALREADY_WISHED);
        }

        productRepository.increaseWishCount(productId);

        return new ProductWishResponseDto(productId, true, productRepository.findWishCount(productId));
    }
}
