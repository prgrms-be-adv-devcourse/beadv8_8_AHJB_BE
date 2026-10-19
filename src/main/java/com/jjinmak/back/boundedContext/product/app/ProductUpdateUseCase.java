package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductAuctionTerms;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto.AuctionTerms;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto.Shipping;
import com.jjinmak.back.boundedContext.product.in.dto.ProductUpdateRequestDto;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductUpdateUseCase {

    private final ProductRepository productRepository;

    public Product updateProduct(Long sellerId, Long productId, ProductUpdateRequestDto request){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.validateEditable(sellerId, LocalDateTime.now());

        Shipping shipping = request.shipping();
        AuctionTerms auctionTerms = request.auctionTerms();

        product.update(
                request.name(), request.category(),
                request.manufacturer(), request.manufacturerEtc(), request.description(),
                shipping != null ? shipping.feeType() : null,
                shipping != null ? shipping.customFee() : null,
                shipping != null ? shipping.bundleAllowed() : null,
                auctionTerms != null
                        ? ProductAuctionTerms.create(
                                auctionTerms.startingPrice(), auctionTerms.instantWinPrice(),
                                auctionTerms.duration(), auctionTerms.customEndAt(),
                                product.getAuctionScheduledAt())
                        : null
        );

        return product;
    }
}
