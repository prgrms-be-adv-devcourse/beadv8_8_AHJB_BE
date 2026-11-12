package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductAuctionTerms;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductRequestAuctionUseCase {

    private final ProductRepository productRepository;
    private final ProductReadThumbnailUseCase productReadThumbnailUseCase;
    private final ApplicationEventPublisher eventPublisher;

    private static final String ENGLISH = "ENGLISH";

    public List<Long> findTargetIds(LocalDateTime now){
        return productRepository.findAuctionRequestTargetIds(now);
    }

    // 시작 예정 시각이 된 상품의 경매 생성을 경매 컨텍스트에 요청한다.
    // 경매 생성이 실패하면 요청 표시도 롤백되어 다음 주기에 다시 시도된다.
    public void requestAuction(Long productId, LocalDateTime now){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        // 조회 이후 삭제되었거나 이미 요청된 경우
        if (!product.isAuctionRequestable(now)){
            return;
        }

        product.requestAuction(now);

        ProductAuctionTerms auctionTerms = product.getAuctionTerms();
        LocalDateTime scheduledAt = product.getAuctionScheduledAt();

        // 시작·종료 시각을 상품에서 확정해서 보낸다. (이벤트가 늦게 도착해도 안내한 시각 그대로 경매가 열린다)
        // 옛 필드(instantWinPrice, duration, customEndAt)는 비워 보낸다. 경매가 더 이상 읽지 않는다.
        // 상품명·썸네일은 정산·채팅이 아직 이 이벤트에서 읽으므로 계속 채운다.
        eventPublisher.publishEvent(new ProductRegisteredEvent(
                product.getId(),
                product.getSeller().getId(),
                product.getShippingFee(),
                auctionTerms.getStartPrice(),
                null,
                0,
                null,
                product.getName(),
                product.getCategory().name(),
                productReadThumbnailUseCase.findThumbnailUrl(product.getId()),
                ENGLISH,
                scheduledAt,
                new ProductRegisteredEvent.EnglishTerms(
                        auctionTerms.getInstantWinPrice(),
                        auctionTerms.resolveEndAt(scheduledAt)),
                null
        ));
    }
}
