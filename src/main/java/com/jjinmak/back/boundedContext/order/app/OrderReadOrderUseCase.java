package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.*;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderReadOrderUseCase {

    private static final Set<OrderState> READ_STATES = EnumSet.of(
            PAID, SHIPPING, SHIPPED, CONFIRMED, REFUND_REQUESTED, REFUNDED
    );

    private final OrderRepository orderRepository;
    private final OrderMemberRepository orderMemberRepository;

    public List<OrderDto> readWinnerOrders(UUID winnerId){

        OrderMember winner = orderMemberRepository.findByUuid(winnerId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        List<Order> orders = orderRepository.findAllByWinnerAndStateIn(winner, READ_STATES);

        return orders.stream().map(
                (order) -> new OrderDto(
                        order.getId(), winnerId, order.getSeller().getUuid(),
                        order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                        order.getState()
                )
        ).toList();
    }

    public List<OrderDto> readSellerOrders(UUID sellerId){

        OrderMember seller = orderMemberRepository.findByUuid(sellerId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        List<Order> orders = orderRepository.findAllBySellerAndStateIn(seller,READ_STATES);

        return orders.stream().map(
                (order) -> new OrderDto(
                        order.getId(), order.getWinner().getUuid(), sellerId,
                        order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                        order.getState()
                )
        ).toList();
    }
}
