package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductImage;
import com.jjinmak.back.boundedContext.product.out.ProductImageRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 업로드된 이미지를 상품에 연결한다. (상품 등록/수정 UseCase에서 사용)
@Service
@RequiredArgsConstructor
public class ProductImageAttachUseCase {

    private final ProductImageRepository productImageRepository;

    // imageIds 순서대로 연결한다. 첫 번째가 대표 이미지(sortOrder 0)
    public void attach(Product product, Long sellerId, List<Long> imageIds){
        List<ProductImage> images = findOwnImages(sellerId, imageIds);

        for (int i = 0; i < images.size(); i++){
            images.get(i).attach(product, i);
        }
    }

    // 상품 수정: 이미지 목록을 통째로 교체한다. 빠진 이미지는 연결을 끊어 정리 대상으로 넘긴다.
    public void replace(Product product, Long sellerId, List<Long> imageIds){
        Set<Long> keepIds = new HashSet<>(imageIds);

        productImageRepository.findAllByProductId(product.getId()).stream()
                .filter(image -> !keepIds.contains(image.getId()))
                .forEach(ProductImage::detach);

        attach(product, sellerId, imageIds);
    }

    // 요청 순서를 유지해서 돌려준다. (중복 id는 요청 DTO에서 막는다)
    private List<ProductImage> findOwnImages(Long sellerId, List<Long> imageIds){
        Map<Long, ProductImage> imagesById = productImageRepository.findAllById(imageIds).stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        if (imagesById.size() != imageIds.size()){
            throw new BusinessException(ProductErrorCode.IMAGE_NOT_FOUND);
        }

        List<ProductImage> images = imageIds.stream().map(imagesById::get).toList();

        if (images.stream().anyMatch(image -> !image.isUploadedBy(sellerId))){
            throw new BusinessException(ProductErrorCode.NOT_IMAGE_UPLOADER);
        }

        return images;
    }
}
