package com.jjinmak.back.boundedContext.auction.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSnapshot {

    private String productName;
    private String category;
    private String thumbnailUrl;

    private ProductSnapshot(String productName, String category, String thumbnailUrl) {
        this.productName = productName;
        this.category = category;
        this.thumbnailUrl = thumbnailUrl;
    }

    public static ProductSnapshot of(String productName, String category, String thumbnailUrl) {
        return new ProductSnapshot(productName, category, thumbnailUrl);
    }
}