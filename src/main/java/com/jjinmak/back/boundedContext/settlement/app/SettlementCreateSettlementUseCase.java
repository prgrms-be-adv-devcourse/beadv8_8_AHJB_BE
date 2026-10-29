package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.out.SettlementRepository;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.product.dto.ProductSummaryDto;
import com.jjinmak.back.shared.product.port.ProductReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementCreateSettlementUseCase {

    private final SettlementRepository settlementRepository;
    private final ProductReadPort productReadPort;

    /**
     * 구매확정 이벤트를 받아 정산서를 만든다 (PENDING, 지급일 = 구매확정한 달의 다음 달 15일).
     * 같은 주문의 정산서가 이미 있으면 아무것도 하지 않는다 (한 주문당 정산서 1개).
     *
     * @param event - 주문 ID, 낙찰자 ID, 판매자 ID, 상품 ID, 낙찰가, 배송비, 구매확정 시각
     */
    public void createSettlement(OrderConfirmPurchaseEvent event) {
        if (settlementRepository.existsByOrderId(event.orderId())) {
            return;
        }

        ProductSummaryDto product = productReadPort.getProductSummary(event.productId());

        settlementRepository.save(new Settlement(
                event.orderId(),
                event.sellerId(),
                product.productId(),
                product.name(),
                event.winningPrice(),
                event.deliveryFee(),
                event.confirmedAt()
        ));
    }
}
