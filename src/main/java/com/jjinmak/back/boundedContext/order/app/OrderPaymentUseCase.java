package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.event.CartCreateOrderEvent;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.order.event.OrderCreatePaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class OrderPaymentUseCase {

    private final OrderSupport orderSupport;
    private final OrderRepository orderRepository;
    private final OrderGroupRepository orderGroupRepository;
    private final EventPublisher eventPublisher;

    /**
     * 전달받은 CartCreateOrderDto를 통해 Order과 OrderGroup을 생성한다.
     * 생성 후에는 지갑 컨텍스트로 orderGroup 정보를 보낸다.
     * @param event - 장바구니 컨텍스트에서 전달받은 "주문을 시도하는 상품들"의 정보
     */
    public void tryPayment(CartCreateOrderEvent event){

        if (event.cartItems().isEmpty()){
            throw new BusinessException(ORDER_BAD_REQUEST);
        }

        List<CartItemDto> cartItems = event.cartItems();

        UUID winnerId = getOnlyWinner(cartItems);

        Long totalPrice = event.totalPrice();

        OrderMember winner = orderSupport.getMember(winnerId);

        OrderGroup orderGroup = orderGroupRepository.save(new OrderGroup(event.totalPrice()));

        List<OrderCreatePaymentEvent.OrderItem> orderItems = new ArrayList<>();

        for (CartItemDto cartItem : cartItems){
            UUID sellerId = cartItem.sellerId();

            OrderMember seller = orderSupport.getMember(sellerId);

            Order order = new Order(orderGroup, winner, seller, cartItem.productId(), cartItem.winningPrice(), cartItem.deliveryFee());

            orderGroup.add(orderRepository.save(order));

            orderItems.add(new OrderCreatePaymentEvent.OrderItem(order.getId(), seller.getId(), order.getWinningPrice(), order.getDeliveryFee()));
        }

        OrderCreatePaymentEvent paymentEvent = new OrderCreatePaymentEvent(
                orderGroup.getId(),
                new OrderCreatePaymentEvent.Member(winner.getId(), winner.getUuid()),
                orderItems,
                totalPrice
        );

        eventPublisher.publish(paymentEvent);
    }

    private UUID getOnlyWinner(List<CartItemDto> dtoList){
        Set<UUID> set = new HashSet<>(dtoList.stream().map(CartItemDto::winnerId).toList());

        if (set.size() > 1){
            throw new BusinessException(ORDER_BAD_REQUEST);
        }

        return set.stream().toList().getFirst();
    }
}
