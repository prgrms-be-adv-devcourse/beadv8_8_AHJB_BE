package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.AuctionDuration;
import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductAuctionTerms;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductRequestAuctionUseCase {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<Long> findTargetIds(LocalDateTime now){
        return productRepository.findAuctionRequestTargetIds(now.minus(Product.EDITABLE_PERIOD));
    }

    // 수정 기간이 끝난 상품의 경매 생성을 경매 컨텍스트에 요청한다.
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
        AuctionDuration duration = auctionTerms.getDuration();

        eventPublisher.publishEvent(new ProductRegisteredEvent(
                product.getId(),
                product.getSeller().getId(),
                auctionTerms.getStartPrice(),
                auctionTerms.getInstantWinPrice(),
                duration.getDays(),
                auctionTerms.getCustomEndAt()
        ));
    }
}
