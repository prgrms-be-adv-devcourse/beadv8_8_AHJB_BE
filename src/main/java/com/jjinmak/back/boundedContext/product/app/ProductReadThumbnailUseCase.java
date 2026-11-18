package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.out.ProductImageRepository;
import com.jjinmak.back.boundedContext.product.out.imageStorage.ProductImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 목록 화면용: 여러 상품의 대표 이미지 URL을 한 번에 조회한다. (N+1 방지)
@Service
@RequiredArgsConstructor
public class ProductReadThumbnailUseCase {

    private final ProductImageRepository productImageRepository;
    private final ProductImageStorage productImageStorage;

    // 상품 id → 대표 이미지 URL (이미지가 없는 상품은 map에 없음)
    public Map<Long, String> findThumbnailUrls(Collection<Long> productIds){
        if (productIds.isEmpty()){
            return Map.of();
        }

        return productImageRepository.findThumbnails(productIds).stream()
                .collect(Collectors.toMap(
                        image -> image.getProduct().getId(),
                        image -> productImageStorage.urlOf(image.getStorageKey()),
                        (first, second) -> first
                ));
    }

    public String findThumbnailUrl(Long productId){
        return findThumbnailUrls(List.of(productId)).get(productId);
    }
}
