package com.jjinmak.search.in.dto;

import com.jjinmak.search.domain.ProductSearchSort;
import com.jjinmak.search.domain.ProductSearchStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

// 검색 조건 (쿼리 스트링으로 받는다). 모든 값은 선택이고, 비어 있으면 그 조건으로는 거르지 않는다.
// - category, manufacturer는 상품 서비스의 enum 이름(CONSOLE, SONY 등)을 그대로 받는다.
//   검색 서비스엔 그 enum이 없어서 문자열로 받고, 없는 값이면 결과가 비어서 나온다.
public record ProductSearchRequestDto(
        String keyword,
        String category,
        String manufacturer,
        ProductSearchStatus status,
        @PositiveOrZero Long minPrice,
        @PositiveOrZero Long maxPrice,
        ProductSearchSort sort,
        @Min(0) Integer page,
        @Min(1) @Max(MAX_SIZE) Integer size
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public ProductSearchRequestDto {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.strip();
        category = blankToNull(category);
        manufacturer = blankToNull(manufacturer);
        page = (page == null) ? DEFAULT_PAGE : page;
        size = (size == null) ? DEFAULT_SIZE : size;
        // 정렬을 안 정했으면 검색어가 있을 땐 정확도순, 없을 땐 최신순
        if (sort == null) {
            sort = (keyword != null) ? ProductSearchSort.RELEVANCE : ProductSearchSort.LATEST;
        }
    }

    @AssertTrue(message = "최소 가격은 최대 가격보다 클 수 없습니다.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
