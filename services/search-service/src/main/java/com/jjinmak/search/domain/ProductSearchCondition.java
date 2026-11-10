package com.jjinmak.search.domain;

import com.jjinmak.contracts.product.domain.Manufacturer;
import com.jjinmak.contracts.product.domain.ProductCategory;

// 검색 조건. 저장소(out)가 웹 요청 DTO(in)에 의존하지 않도록 따로 둔다.
// null인 조건은 거르지 않는다. (keyword가 null이면 검색어 없이 조건으로만 거른 목록)
public record ProductSearchCondition(
        String keyword,
        ProductCategory category,
        Manufacturer manufacturer,
        ProductSearchStatus status,
        Long minPrice,
        Long maxPrice,
        ProductSearchSort sort,
        int page,
        int size
) {
    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    // 건너뛸 문서 수 (2페이지, 20개씩이면 20)
    public long offset() {
        return (long) page * size;
    }
}
