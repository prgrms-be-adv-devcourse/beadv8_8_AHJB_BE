package com.jjinmak.boundedcontext.product.product.app;

import com.jjinmak.boundedcontext.product.product.domain.Product;
import com.jjinmak.boundedcontext.product.product.domain.ProductErrorCode;
import com.jjinmak.boundedcontext.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ProductStartAuctionUseCase {

    private final ProductRepository productRepository;

    // 경매가 생성되면 상품에 현재 회차의 경매 id를 기록한다.
    public void startAuction(Long productId, Long auctionId){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.startAuction(auctionId);
    }
}
