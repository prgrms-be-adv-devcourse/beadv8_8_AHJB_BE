package com.jjinmak.order.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.order.app.dto.OrderDto;
import com.jjinmak.order.domain.Order;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.domain.OrderState;
import com.jjinmak.order.out.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.jjinmak.order.domain.OrderState.*;
import static com.jjinmak.order.exception.OrderErrorCode.ORDER_NOT_FOUND;


@Service
@RequiredArgsConstructor
public class OrderReadOrderUseCase {

    private static final Set<OrderState> READ_STATES = EnumSet.of(
            PAID, SHIPPING, DELIVERY_ERROR, SHIPPED, CONFIRMED, REFUND_REQUESTED, REFUNDED
    );

    private final OrderSupport orderSupport;
    private final OrderRepository orderRepository;

    public List<OrderDto> readWinnerOrders(Long winnerId, int page, int size){

        OrderMember winner = orderSupport.getMember(winnerId);

        Page<Order> orders = orderRepository.findAllByWinnerAndStateIn(winner, READ_STATES, PageRequest.of(Math.max(page, 0), Math.clamp(size, 10, 50)));

        return orders.stream().map(
                (order) -> new OrderDto(
                        order.getId(), winner.getUuid(), order.getSeller().getUuid(),
                        order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                        order.getState(), order.getConfirmExpiredAt(), order.getCreatedAt(), order.getUpdatedAt()
                )
        ).toList();
    }

    public List<OrderDto> readSellerOrders(Long sellerId, int page, int size){

        OrderMember seller = orderSupport.getMember(sellerId);

        Page<Order> orders = orderRepository.findAllBySellerAndStateIn(seller,READ_STATES, PageRequest.of(Math.max(page, 0), Math.clamp(size, 10, 50)));

        return orders.stream().map(
                (order) -> new OrderDto(
                        order.getId(), order.getWinner().getUuid(), seller.getUuid(),
                        order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                        order.getState(), order.getConfirmExpiredAt(), order.getCreatedAt(), order.getUpdatedAt()
                )
        ).toList();
    }

    public OrderDto readOrder(Long memberId, Long orderId){

        OrderMember member = orderSupport.getMember(memberId);

        Order order = orderRepository.findByIdAndStateInWithWinnerAndSeller(orderId, READ_STATES)
                .orElseThrow(() -> new BusinessException(ORDER_NOT_FOUND));

        order.validateParticipant(member);

        return new OrderDto(
                order.getId(), order.getWinner().getUuid(), order.getSeller().getUuid(),
                order.getProductId(), order.getWinningPrice(), order.getDeliveryFee(),
                order.getState(), order.getConfirmExpiredAt(), order.getCreatedAt(), order.getUpdatedAt()
        );
    }
}
