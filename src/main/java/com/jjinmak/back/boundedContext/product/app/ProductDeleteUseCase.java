package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.out.ProductRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductDeleteUseCase {

    private final ProductRepository productRepository;

    // 경매 요청 전에만 삭제할 수 있으므로 경매 컨텍스트에 알림 x
    public void deleteProduct(Long sellerId, Long productId){
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        product.validateEditable(sellerId, now);

        product.delete(now);
    }
}
