package com.jjinmak.boundedcontext.product.product.app;

import com.jjinmak.boundedcontext.product.product.domain.Product;
import com.jjinmak.boundedcontext.product.product.domain.ProductErrorCode;
import com.jjinmak.boundedcontext.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductStartAuctionUseCase {

    private final ProductRepository productRepository;

    // 경매 시작 이벤트를 받아 상품에 현재 회차의 경매 id와 진행 중 상태를 기록한다.
    public void startAuction(AuctionStartedEvent event){
        startAuction(event.productId(), event.auctionId(), event.endAt());
    }

    // 경매가 생성되면 상품에 현재 회차의 경매 id와 진행 중 상태를 기록한다. (이미 진행 중이면 상태는 그대로)
    public void startAuction(Long productId, Long auctionId, LocalDateTime endAt){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.startAuction(auctionId, endAt);
    }
}
