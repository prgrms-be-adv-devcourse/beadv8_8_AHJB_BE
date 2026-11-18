package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductImageFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImageCleanupScheduler {

    private final ProductImageFacade productImageFacade;

    // 1시간마다 상품에 연결되지 않은 채 24시간이 지난 이미지를 삭제한다.
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    public void cleanupUnattachedImages(){
        List<Long> imageIds = productImageFacade.findCleanupTargetIds(LocalDateTime.now());

        for (Long imageId : imageIds){
            try {
                productImageFacade.deleteUnattachedImage(imageId);
            } catch (Exception e) {
                // 실패한 이미지는 DB 행이 남아 다음 주기에 다시 시도된다.
                log.warn("상품 이미지 정리 실패 imageId={}", imageId, e);
            }
        }
    }
}
