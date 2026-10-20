package com.jjinmak.back.shared.product.port;

import com.jjinmak.back.shared.product.dto.ProductSummaryDto;

import java.util.Collection;
import java.util.List;

public interface ProductReadPort {

    ProductSummaryDto getProductSummary(Long productId);

    // 목록 화면용 일괄 조회 (경매 목록 등). 없는 상품은 결과에서 빠진다.
    List<ProductSummaryDto> getProductSummaries(Collection<Long> productIds);
}
