package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.product.dto.ProductSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductReadSummaryUseCase {

    private final ProductRepository productRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;

    // 다른 컨텍스트에 상품 요약 정보를 제공한다. (거래가 끝난 상품도 조회되도록 삭제 여부와 무관하게 조회)
    public ProductSummaryDto getProductSummary(Long productId){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return new ProductSummaryDto(product.getId(), product.getName(),
                productReadThumbnailUseCase.findThumbnailUrl(productId));
    }

    public List<ProductSummaryDto> getProductSummaries(Collection<Long> productIds){
        if (productIds.isEmpty()){
            return List.of();
        }

        Map<Long, String> thumbnailUrls = productReadThumbnailUseCase.findThumbnailUrls(productIds);

        return productRepository.findAllById(productIds).stream()
                .map(product -> new ProductSummaryDto(product.getId(), product.getName(),
                        thumbnailUrls.get(product.getId())))
                .toList();
    }
}
