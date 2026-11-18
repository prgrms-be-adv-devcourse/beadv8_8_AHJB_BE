package com.jjinmak.boundedcontext.product.product.app;

import com.jjinmak.boundedcontext.product.product.domain.Product;
import com.jjinmak.boundedcontext.product.product.domain.ProductErrorCode;
import com.jjinmak.boundedcontext.product.product.in.dto.ProductAuctionOpenDto;
import com.jjinmak.boundedcontext.product.product.in.dto.ProductRelistDto;
import com.jjinmak.boundedcontext.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductRelistUseCase {

    private final ProductRepository productRepository;

    // 상품을 다음 회차로 넘기고 새 회차의 판매 조건을 돌려준다.
    // 경매 생성은 market 흐름(MarketRelistUseCase)이 같은 트랜잭션에서 이어서 한다.
    public ProductRelistDto relist(Long sellerId, Long productId, LocalDateTime now) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        Long previousAuctionId = product.getCurrentAuctionId();
        LocalDateTime endAt = product.relist(sellerId, now);

        return new ProductRelistDto(previousAuctionId, ProductAuctionOpenDto.of(product, endAt));
    }
}
