package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductAuctionTerms;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.in.dto.ProductCreateRequestDto.AuctionTerms;
import com.jjinmak.product.product.in.dto.ProductCreateRequestDto.Shipping;
import com.jjinmak.product.product.in.dto.ProductUpdateRequestDto;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.product.event.ProductChangeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductUpdateUseCase {

    private final ProductRepository productRepository;
    private final ProductImageAttachUseCase productImageAttachUseCase;
    private final ProductPublishChangedUseCase productPublishChangedUseCase;

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

        // 이미지 목록을 보내면 통째로 교체한다. (안 보내면 기존 유지)
        if (request.imageIds() != null){
            productImageAttachUseCase.replace(product, sellerId, request.imageIds());
        }

        productPublishChangedUseCase.publish(product, ProductChangeType.UPDATED);

        return product;
    }
}
