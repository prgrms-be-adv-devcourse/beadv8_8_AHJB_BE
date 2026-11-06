package com.jjinmak.product.product.app;

import com.jjinmak.product.product.domain.Product;
import com.jjinmak.product.product.domain.ProductErrorCode;
import com.jjinmak.product.product.out.ProductRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.product.event.ProductChangeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductDeleteUseCase {

    private final ProductRepository productRepository;
    private final ProductPublishChangedUseCase productPublishChangedUseCase;

    // 경매 요청 전에만 삭제할 수 있으므로 경매 컨텍스트에 알림 x
    // 대신 검색 결과에서는 빠져야 하므로 상품 변경(삭제) 이벤트를 발행한다.
    public void deleteProduct(Long sellerId, Long productId){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        product.validateEditable(sellerId, now);

        product.delete(now);

        productPublishChangedUseCase.publish(product, ProductChangeType.DELETED);
    }
}
