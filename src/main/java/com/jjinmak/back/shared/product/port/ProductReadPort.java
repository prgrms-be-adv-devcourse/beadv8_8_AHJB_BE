package com.jjinmak.back.shared.product.port;

import com.jjinmak.back.shared.product.dto.ProductSummaryDto;

public interface ProductReadPort {

    ProductSummaryDto getProductSummary(Long productId);
}
