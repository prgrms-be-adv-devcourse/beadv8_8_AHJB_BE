package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductCategory;
import com.jjinmak.back.boundedContext.product.domain.ProductWish;

import java.time.LocalDateTime;

public record ProductWishDto(
        Long productId,
        String productName,
        ProductCategory category,
        Long startPrice,
        int wishCount,
        LocalDateTime wishedAt
) {
    public static ProductWishDto from(ProductWish wish) {
        Product product = wish.getProduct();

        return new ProductWishDto(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getAuctionTerms().getStartPrice(),
                product.getWishCount(),
                wish.getCreatedAt()
        );
    }
}
