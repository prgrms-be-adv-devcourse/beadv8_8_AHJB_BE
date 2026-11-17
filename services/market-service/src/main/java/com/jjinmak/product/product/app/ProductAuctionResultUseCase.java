package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductAuctionFailureReason;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 경매 결과(낙찰/무산) 이벤트를 받아 상품의 경매 상태를 갱신한다.
@Service
@RequiredArgsConstructor
public class ProductAuctionResultUseCase {

    private final ProductRepository productRepository;

    public void markSold(AuctionWonEvent event){
        findProduct(event.productId()).markAuctionSold(event.auctionId());
    }

    public void markFailed(AuctionFailedEvent event){
        findProduct(event.productId()).markAuctionFailed(
                event.auctionId(),
                ProductAuctionFailureReason.from(event.failureReason())
        );
    }

    // 삭제된 상품도 경매 결과는 기록한다. (경매 시작 후에는 삭제할 수 없지만 방어적으로 findById 사용)
    private Product findProduct(Long productId){
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
