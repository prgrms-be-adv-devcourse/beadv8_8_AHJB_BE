package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductStartAuctionUseCase {

    private final ProductRepository productRepository;

    // 경매 시작 이벤트를 받아 상품에 현재 회차의 경매 id와 진행 중 상태를 기록한다.
    public void startAuction(AuctionStartedEvent event){
        Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.startAuction(event.auctionId(), event.endAt());
    }
}
