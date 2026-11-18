package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementErrorCode;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementProduct;
import com.jjinmak.back.boundedContext.settlement.out.SettlementProductRepository;
import com.jjinmak.back.boundedContext.settlement.out.SettlementRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementCreateSettlementUseCase {

    private final SettlementRepository settlementRepository;
    private final SettlementProductRepository settlementProductRepository;

    /**
     * 구매확정 이벤트를 받아 정산서를 만든다 (PENDING, 지급일 = 구매확정한 달의 다음 달 15일).
     * 같은 주문의 정산서가 이미 있으면 아무것도 하지 않는다 (한 주문당 정산서 1개).
     * 상품명은 product.registered 이벤트로 저장해 둔 복사본에서 읽는다.
     * 복사본이 없으면 예외를 던져 Kafka 재시도 → DLT로 보낸다 (경매 요청 이벤트가 먼저 와야 정상).
     *
     * @param event - 주문 ID, 낙찰자 ID, 판매자 ID, 상품 ID, 낙찰가, 배송비, 구매확정 시각
     */
    public void createSettlement(OrderConfirmPurchaseEvent event) {
        if (settlementRepository.existsByOrderId(event.orderId())) {
            return;
        }

        SettlementProduct product = settlementProductRepository.findById(event.productId())
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.PRODUCT_NOT_FOUND));

        settlementRepository.save(new Settlement(
                event.orderId(),
                event.sellerId(),
                event.productId(),
                product.getName(),
                event.winningPrice(),
                event.deliveryFee(),
                event.confirmedAt()
        ));
    }
}