package com.jjinmak.search.app;

import com.jjinmak.search.domain.ProductSearchCondition;
import com.jjinmak.search.domain.ProductSearchResult;
import com.jjinmak.search.in.dto.ProductSearchItemDto;
import com.jjinmak.search.in.dto.ProductSearchListResponseDto;
import com.jjinmak.search.in.dto.ProductSearchRequestDto;
import com.jjinmak.search.out.elasticsearch.ProductSearchStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// 검색 조건으로 상품을 찾아 목록으로 돌려준다.
// (삭제된 상품, 아직 상품 정보가 없는 문서는 저장소에서 항상 걸러진다)
@Service
@RequiredArgsConstructor
public class ProductSearchUseCase {

    private final ProductSearchStore store;

    public ProductSearchListResponseDto search(ProductSearchRequestDto request) {
        ProductSearchCondition condition = new ProductSearchCondition(
                request.keyword(),
                request.category(),
                request.manufacturer(),
                request.status(),
                request.minPrice(),
                request.maxPrice(),
                request.sort(),
                request.page(),
                request.size()
        );

        ProductSearchResult result = store.search(condition);

        List<ProductSearchItemDto> content = result.documents().stream()
                .map(ProductSearchItemDto::of)
                .toList();
        return ProductSearchListResponseDto.of(content, condition.page(), condition.size(), result.totalElements());
    }
}
