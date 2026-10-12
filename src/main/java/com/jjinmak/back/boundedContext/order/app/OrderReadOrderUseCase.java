package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderReadOrderUseCase {

    private static final Set<OrderState> READ_STATES = EnumSet.of(
            PAID, SHIPPING, SHIPPED, CONFIRMED, REFUND_REQUESTED, REFUNDED
    );

    private final OrderSupport orderSupport;
    private final OrderRepository orderRepository;

    public List<OrderDto> readWinnerOrders(UUID winnerId){

        OrderMember winner = orderSupport.getMember(winnerId);

        List<Order> orders = orderRepository.findAllByWinnerAndStateIn(winner, READ_STATES);

        return orders.stream().map(
                (order) -> new OrderDto(
                        order.getId(), winnerId, order.getSeller().getUuid(),
                        order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                        order.getState(), order.getCreatedAt(), order.getUpdatedAt()
                )
        ).toList();
    }

    public List<OrderDto> readSellerOrders(UUID sellerId){

        OrderMember seller = orderSupport.getMember(sellerId);

        List<Order> orders = orderRepository.findAllBySellerAndStateIn(seller,READ_STATES);

        return orders.stream().map(
                (order) -> new OrderDto(
                        order.getId(), order.getWinner().getUuid(), sellerId,
                        order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                        order.getState(), order.getCreatedAt(), order.getUpdatedAt()
                )
        ).toList();
    }

    public OrderDto readOrder(UUID memberId, Long orderId){

        OrderMember member = orderSupport.getMember(memberId);

        Order order = orderRepository.findByIdAndStateInWithWinnerAndSeller(orderId, READ_STATES)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));

        order.validateParticipant(member);

        return new OrderDto(
                order.getId(), order.getWinner().getUuid(), order.getSeller().getUuid(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                order.getState(), order.getCreatedAt(), order.getUpdatedAt()
        );
    }
}
