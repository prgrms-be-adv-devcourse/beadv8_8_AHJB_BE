package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductCategory;
import com.jjinmak.back.boundedContext.product.in.dto.ProductUpcomingItemDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductUpcomingListResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductReadUpcomingUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductRepository productRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;

    // 아직 경매가 시작되지 않은 상품을 시작 임박순으로 조회한다.
    public ProductUpcomingListResponseDto readUpcoming(String category, int page, int size){
        Page<Product> products = productRepository.findUpcoming(
                parseCategory(category),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, String> thumbnailUrls = productReadThumbnailUseCase.findThumbnailUrls(productIds);

        List<ProductUpcomingItemDto> content = products.stream()
                .map(product -> ProductUpcomingItemDto.of(product, thumbnailUrls.get(product.getId())))
                .toList();

        return new ProductUpcomingListResponseDto(
                content,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.hasNext(),
                LocalDateTime.now()
        );
    }

    // 경매 목록 API와 같이 문자열로 받는다. 비어 있으면 전체, 잘못된 값이면 400
    private ProductCategory parseCategory(String category){
        if (category == null || category.isBlank()){
            return null;
        }
        try {
            return ProductCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
    }
}
