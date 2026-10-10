package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_NOT_FOUND;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class OrderRefundResultUseCase {

    private final OrderRepository orderRepository;

    public void acceptRefund(RefundDto refund){
        Long orderId = refund.orderId();

        Order order = checkOrder(orderId);

        order.updateState(REFUNDED);

        // TODO: 환불 처리됨 이벤트를 발행해야 하나?
    }

    public void rejectRefund(RefundDto refund){
        Long orderId = refund.orderId();

        Order order = checkOrder(orderId);

        order.updateState(CONFIRMED);

        // TODO: 정산 컨텍스트로 구매 확정됨 이벤트 발행
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderId, order.getWinner().getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                LocalDateTime.now()
        );
    }

    private Order checkOrder(Long orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));

        if (!order.getState().equals(REFUND_REQUESTED)){
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);
        }

        return order;
    }
}
