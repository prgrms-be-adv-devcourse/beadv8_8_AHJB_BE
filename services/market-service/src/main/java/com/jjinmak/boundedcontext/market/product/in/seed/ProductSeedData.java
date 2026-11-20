package com.jjinmak.boundedcontext.market.product.in.seed;

import com.jjinmak.boundedcontext.market.product.domain.AuctionDuration;
import com.jjinmak.boundedcontext.market.product.domain.ShippingFeeType;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;

import java.util.List;

// seed/products.json 한 줄. shippingFee가 없으면 무료배송, instantWinPrice가 없으면 즉시 낙찰 없음.
public record ProductSeedData(
        String name,
        ProductCategory category,
        Manufacturer manufacturer,
        String manufacturerEtc,
        String description,
        Long startingPrice,
        Long instantWinPrice,
        AuctionDuration duration,
        Long shippingFee
) {
    // 시드로 등록한 상품을 구분하는 정책 버전 (개수 제한과 나중에 정리할 때 쓴다)
    public static final String POLICY_VERSION = "SEED";

    public ProductCreateRequestDto toRequest(Long imageId) {
        ShippingFeeType feeType = shippingFee == null ? ShippingFeeType.FREE : ShippingFeeType.CUSTOM;

        return new ProductCreateRequestDto(
                true, POLICY_VERSION,
                name, category, manufacturer, manufacturerEtc,
                List.of(imageId), description,
                new ProductCreateRequestDto.Shipping(feeType, shippingFee, false),
                new ProductCreateRequestDto.AuctionTerms(startingPrice, instantWinPrice, duration, null)
        );
    }

    // 카테고리별 샘플 이미지 (seed/images/{category}.png)
    public String imagePath() {
        return "seed/images/" + category.name().toLowerCase() + ".png";
    }
}
