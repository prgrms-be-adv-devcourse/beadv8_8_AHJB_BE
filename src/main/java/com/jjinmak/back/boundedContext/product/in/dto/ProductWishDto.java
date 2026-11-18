package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductCategory;
import com.jjinmak.back.boundedContext.product.domain.ProductWish;

import java.time.LocalDateTime;

public record ProductWishDto(
        Long productId,
        String productName,
        String thumbnailUrl,    // 대표 이미지 (없으면 null)
        ProductCategory category,
        Long startPrice,
        int wishCount,
        LocalDateTime wishedAt
) {
    public static ProductWishDto of(ProductWish wish, String thumbnailUrl) {
        Product product = wish.getProduct();

        return new ProductWishDto(
                product.getId(),
                product.getName(),
                thumbnailUrl,
                product.getCategory(),
                product.getAuctionTerms().getStartPrice(),
                product.getWishCount(),
                wish.getCreatedAt()
        );
    }
}
