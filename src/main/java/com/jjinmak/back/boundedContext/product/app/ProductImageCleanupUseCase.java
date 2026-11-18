package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.out.ProductImageRepository;
import com.jjinmak.back.boundedContext.product.out.imageStorage.ProductImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 업로드만 하고 상품에 연결하지 않은(또는 수정으로 빠진) 이미지를 정리한다.
@Service
@RequiredArgsConstructor
public class ProductImageCleanupUseCase {

    // 업로드 후 이 시간이 지나도 상품에 연결되지 않으면 삭제한다.
    public static final Duration UNATTACHED_RETENTION = Duration.ofHours(24);
    private static final int BATCH_SIZE = 100;

    private final ProductImageRepository productImageRepository;
    private final ProductImageStorage productImageStorage;

    public List<Long> findTargetIds(LocalDateTime now){
        return productImageRepository.findUnattachedIdsBefore(
                now.minus(UNATTACHED_RETENTION), PageRequest.of(0, BATCH_SIZE));
    }

    // 저장소 파일을 먼저 지운다. 저장소 삭제가 실패하면 DB 행이 남아 다음 주기에 다시 시도된다.
    public void delete(Long imageId){
        productImageRepository.findById(imageId)
                .filter(image -> image.getProduct() == null)
                .ifPresent(image -> {
                    productImageStorage.delete(image.getStorageKey());
                    productImageRepository.delete(image);
                });
    }
}
