package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.in.dto.ProductDetailDto;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.boundedContext.product.out.ProductWishRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.review.dto.SellerRatingDto;
import com.jjinmak.back.shared.review.port.ReviewReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductReadDetailUseCase {

    private final ProductRepository productRepository;
    private final ProductWishRepository productWishRepository;
    private final ReviewReadPort reviewReadPort;

    // memberId는 비로그인이면 null (찜 여부, 본인 여부는 false)
    public ProductDetailDto readDetail(Long memberId, Long productId){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        SellerRatingDto sellerRating = reviewReadPort.getSellerRating(product.getSeller().getId());

        boolean wished = memberId != null && productWishRepository.existsByMemberIdAndProductId(memberId, productId);

        return ProductDetailDto.of(product, sellerRating, wished, product.isSoldBy(memberId));
    }
}
