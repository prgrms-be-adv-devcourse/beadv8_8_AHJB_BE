package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductFacade;
import com.jjinmak.back.shared.product.dto.ProductSummaryDto;
import com.jjinmak.back.shared.product.port.ProductReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductReadPortAdapter implements ProductReadPort {

    private final ProductFacade productFacade;

    @Override
    public ProductSummaryDto getProductSummary(Long productId) {
        return productFacade.getProductSummary(productId);
    }
}
