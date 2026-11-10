package com.jjinmak.settlement.app;

import com.jjinmak.settlement.domain.SettlementProduct;
import com.jjinmak.settlement.out.SettlementProductRepository;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementSaveProductUseCase {

    private final SettlementProductRepository settlementProductRepository;

    /**
     * 경매 요청 이벤트를 받아 상품명 복사본을 저장한다.
     * 같은 상품의 복사본이 이미 있으면 아무것도 하지 않는다 (Kafka 중복 수신 대비).
     *
     * @param event - 상품 ID, 상품명 (그 외 필드는 정산에서 쓰지 않는다)
     */
    public void saveProduct(ProductRegisteredEvent event) {
        if (settlementProductRepository.existsById(event.productId())) {
            return;
        }

        settlementProductRepository.save(new SettlementProduct(event.productId(), event.productName()));
    }
}