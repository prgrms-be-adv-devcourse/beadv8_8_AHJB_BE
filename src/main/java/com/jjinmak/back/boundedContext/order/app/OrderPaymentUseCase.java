package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.exception.BadRequestException;
import com.jjinmak.back.global.exception.NotFoundException;
import com.jjinmak.back.shared.cart.dto.CartCreateOrderDto;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.order.dto.OrderCreatePaymentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPaymentUseCase {

    private final OrderRepository orderRepository;
    private final OrderGroupRepository orderGroupRepository;
    private final OrderMemberRepository orderMemberRepository;

    /**
     * 전달받은 CartCreateOrderDto를 통해 Order과 OrderGroup을 생성한다.
     * 생성 후에는 지갑 컨텍스트로 orderGroup 정보를 보낸다.
     * @param dto - 장바구니 컨텍스트에서 전달받은 "주문을 시도하는 상품들"의 정보
     * @return OrderGroup - 한 번에 결제를 시도하는 주문들의 묶음
     */
    public OrderGroup tryPayment(CartCreateOrderDto dto){

        if (dto.cartItems().isEmpty()){
            throw new BadRequestException("ORDER001", "올바르지 않은 주문 요청입니다.");
        }

        List<CartItemDto> cartItems = dto.cartItems();

        UUID winnerId = getOnlyWinner(cartItems);

        Long totalPrice = dto.totalPrice();

        OrderMember winner = orderMemberRepository.findByUuid(winnerId)
                .orElseThrow(() -> new NotFoundException("COMMON103", "존재하지 않는 회원입니다."));

        OrderGroup orderGroup = orderGroupRepository.save(new OrderGroup(dto.totalPrice()));

        for (CartItemDto cartItem : cartItems){
            UUID sellerId = cartItem.sellerId();

            OrderMember seller = orderMemberRepository.findByUuid(sellerId)
                    .orElseThrow(() -> new NotFoundException("COMMON103", "존재하지 않는 회원입니다."));

            Order order = new Order(orderGroup, winner, seller, cartItem.productId(), cartItem.winningPrice(), cartItem.deliveryFee());

            orderGroup.add(orderRepository.save(order));
        }

        // TODO: 생성된 orderGroup 정보를 지갑 컨텍스트로 전송한다.
        OrderCreatePaymentDto paymentDto = new OrderCreatePaymentDto(orderGroup.getId(), winnerId, totalPrice);

        return orderGroup;
    }

    private UUID getOnlyWinner(List<CartItemDto> dtoList){
        Set<UUID> set = new HashSet<>(dtoList.stream().map(CartItemDto::winnerId).toList());

        if (set.size() > 1){
            throw new BadRequestException("ORDER002", "주문 간 낙찰자가 서로 다릅니다.");
        }

        return set.stream().toList().getFirst();
    }
}
