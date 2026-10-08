package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.*;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderConfirmPurchaseUseCase {

    private final OrderRepository orderRepository;
    private final OrderMemberRepository orderMemberRepository;

    public void confirmPurchase(UUID memberId, Long orderId){
        // TODO: memberId가 정해지면 다시 리팩토링
        OrderMember winner = orderMemberRepository.findByUuid(memberId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        Order order = orderRepository.findByIdWithWinnerAndSeller(orderId)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));

        if (!order.getWinner().getId().equals(winner.getId())){
            throw new BusinessException(ORDER_FORBIDDEN);
        }

        if (!order.getState().equals(SHIPPED)){
            throw new BusinessException(ORDER_STATE_BAD_REQUEST);
        }

        order.updateState(CONFIRMED);

        // TODO: 구매 확정 이벤트 발행
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderId, winner.getId(), order.getSeller().getId(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                LocalDateTime.now()
        );
    }
}
