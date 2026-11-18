package com.jjinmak.back.boundedContext.product.in.dto;

import com.jjinmak.back.boundedContext.product.domain.Manufacturer;
import com.jjinmak.back.boundedContext.product.domain.ProductCategory;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto.AuctionTerms;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto.Shipping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

// 값이 있는 항목만 수정한다. (null이면 기존 값 유지)
// shipping, auctionTerms는 보낼 때 내부 필수값을 모두 채워서 통째로 교체한다.
public record ProductUpdateRequestDto(
        @Size(max = 100)
        @Pattern(regexp = "(?s).*\\S.*")
        String name,

        ProductCategory category,

        Manufacturer manufacturer,

        @Size(max = 50)
        String manufacturerEtc,

        @Pattern(regexp = "(?s).*\\S.*")
        String description,

        // 보내면 이미지 목록 전체를 교체한다. (순서 = 노출 순서, 첫 번째가 대표 이미지)
        @Size(min = 1, max = 10)
        @UniqueElements
        List<@NotNull Long> imageIds,

        @Valid
        Shipping shipping,

        @Valid
        AuctionTerms auctionTerms
) {
}
