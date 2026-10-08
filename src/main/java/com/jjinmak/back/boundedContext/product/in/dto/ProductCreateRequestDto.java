package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.Manufacturer;
import com.jjinmak.back.boundedContext.product.domain.ProductCategory;
import com.jjinmak.back.boundedContext.product.domain.ShippingFeeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UniqueElements;

import java.time.LocalDateTime;
import java.util.List;

public record ProductCreateRequestDto(
        @NotNull
        Boolean policyAgreed,

        @NotBlank
        String policyVersion,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        ProductCategory category,

        @NotNull
        Manufacturer manufacturer,

        @Size(max = 50)
        String manufacturerEtc,

        // TODO: presigned url 발급 API 구현 후 이미지 연결
        @NotEmpty
        @Size(max = 10)
        @UniqueElements
        List<@NotNull Long> imageIds,

        @NotBlank
        String description,

        @NotNull
        @Valid
        Shipping shipping,

        @NotNull
        @Valid
        AuctionTerms auctionTerms
) {
    public record Shipping(
            @NotNull
            ShippingFeeType feeType,

            @PositiveOrZero
            Long customFee,

            @NotNull
            Boolean bundleAllowed
    ) {
    }

    public record AuctionTerms(
            @NotNull
            @Positive
            Long startingPrice,

            @Positive
            Long instantWinPrice,

            @NotNull
            AuctionDuration duration,

            @Future
            LocalDateTime customEndAt
    ) {
    }
}
