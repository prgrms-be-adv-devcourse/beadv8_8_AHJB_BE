package com.jjinmak.boundedcontext.market.product.in.dto;

import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductCreateRequestDto.Shipping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UniqueElements;

import java.time.LocalDateTime;
import java.util.List;

// 관리자 네덜란드식 이벤트 상품 등록 요청
// 상품 정보는 판매자 등록과 같고, 경매 조건과 오픈 시각만 다르다. (판매 정책 동의는 받지 않는다)
public record ProductDutchCreateRequestDto(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        ProductCategory category,

        @NotNull
        Manufacturer manufacturer,

        @Size(max = 50)
        String manufacturerEtc,

        // 이미지 업로드 API(POST /products/images)로 받은 id. 순서 = 노출 순서, 첫 번째가 대표 이미지
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
        DutchTerms auctionTerms,

        // 이벤트 오픈 시각 (= 경매 시작 시각). 지금보다 뒤여야 한다.
        @NotNull
        LocalDateTime openAt
) {
    // 허용 값(하락률 1/2/3, 하락 주기 30/60/90/120)과 금액 규칙은 도메인에서 검증한다.
    public record DutchTerms(
            @NotNull
            @Positive
            Long startingPrice,

            @NotNull
            @PositiveOrZero
            Long floorPrice,

            @NotNull
            Integer dropRatePercent,

            @NotNull
            Integer dropIntervalSec
    ) {
    }
}
