package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.in.dto.ProductImageUploadResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageFacade {

    private final ProductImageUploadUseCase productImageUploadUseCase;
    private final ProductImageCleanupUseCase productImageCleanupUseCase;

    @Transactional
    public ProductImageUploadResponseDto upload(Long uploaderId, MultipartFile file){
        return productImageUploadUseCase.upload(uploaderId, file);
    }

    @Transactional(readOnly = true)
    public List<Long> findCleanupTargetIds(LocalDateTime now){
        return productImageCleanupUseCase.findTargetIds(now);
    }

    // 이미지마다 별도 트랜잭션으로 처리해서, 하나가 실패해도 나머지는 정리된다.
    @Transactional
    public void deleteUnattachedImage(Long imageId){
        productImageCleanupUseCase.delete(imageId);
    }
}
