package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.in.dto.ProductDetailDto;
import com.jjinmak.product.product.out.ProductImageRepository;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.product.product.out.ProductWishRepository;
import com.jjinmak.product.product.out.imageStorage.ProductImageStorage;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.review.dto.SellerRatingDto;
import com.jjinmak.contracts.review.port.ReviewReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductReadDetailUseCase {

    private final ProductRepository productRepository;
    private final ProductWishRepository productWishRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageStorage productImageStorage;
    private final ReviewReadPort reviewReadPort;

    // memberId는 비로그인이면 null (찜 여부, 본인 여부는 false)
    public ProductDetailDto readDetail(Long memberId, Long productId){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        SellerRatingDto sellerRating = reviewReadPort.getSellerRating(product.getSeller().getId());

        boolean wished = memberId != null && productWishRepository.existsByMemberIdAndProductId(memberId, productId);

        List<String> imageUrls = productImageRepository.findAllByProductId(productId).stream()
                .map(image -> productImageStorage.urlOf(image.getStorageKey()))
                .toList();

        return ProductDetailDto.of(product, imageUrls, sellerRating, wished, product.isSoldBy(memberId));
    }
}
