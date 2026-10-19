package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.auction.event.AuctionStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductStartAuctionUseCase {

    private final ProductRepository productRepository;

    // 경매 시작 이벤트를 받아 상품에 현재 회차의 경매 id를 기록한다.
    public void startAuction(AuctionStartedEvent event){
        Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.startAuction(event.auctionId());
    }
}
