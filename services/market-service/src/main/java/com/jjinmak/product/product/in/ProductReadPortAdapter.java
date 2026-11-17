package com.jjinmak.product.product.in;

import com.jjinmak.product.product.app.ProductFacade;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;
import com.jjinmak.contracts.product.port.ProductReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductReadPortAdapter implements ProductReadPort {

    private final ProductFacade productFacade;

    @Override
    public ProductSummaryDto getProductSummary(Long productId) {
        return productFacade.getProductSummary(productId);
    }

    @Override
    public List<ProductSummaryDto> getProductSummaries(Collection<Long> productIds) {
        return productFacade.getProductSummaries(productIds);
    }
}
