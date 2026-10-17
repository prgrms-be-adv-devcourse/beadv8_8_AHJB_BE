package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_PAYMENT_EXPIRED;

@Service
@RequiredArgsConstructor
public class CartRequestPaymentUseCase {

    private final OrderSupport orderSupport;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderGroupRepository orderGroupRepository;

    /**
     * 낙찰자가 해당하는 상품을 보유하고 있는지 검사, 주문서를 생성한 후 결제 컨텍스트에 요청(예정)
     * @param winnerId   - 조회의 주체가 되는 낙찰자
     * @param productIds - 결제를 시도하는 상품의 id 리스트
     */
    public void requestPayment(Long winnerId, List<Long> productIds){
        OrderMember winner = orderSupport.getMember(winnerId);

        Set<Long> productIdSet = new HashSet<>(productIds);

        List<CartItem> cartItems = cartItemRepository.findAllByWinnerAndProductIdIn(winner, productIdSet);

        if (productIdSet.size() != cartItems.size()){
            throw new BusinessException(CART_ITEM_FORBIDDEN);
        }

        LocalDateTime now = LocalDateTime.now();

        if (cartItems.stream().anyMatch(i -> i.getPaymentDueAt().isBefore(now))){
            throw new BusinessException(CART_ITEM_PAYMENT_EXPIRED);
        }

        long totalPrice = cartItems.stream()
                .mapToLong(i -> i.getWinningPrice() + i.getDeliveryFee())
                .sum();

        OrderGroup orderGroup = orderGroupRepository.save(new OrderGroup(totalPrice));

        for (CartItem item : cartItems){
            OrderMember seller = item.getSeller();

            Order order = new Order(
                    orderGroup, winner, seller,
                    item.getAuctionId(), item.getProductId(),
                    item.getWinningPrice(), item.getDeliveryFee()
            );

            orderGroup.add(orderRepository.save(order));
        }

        // TODO: 결제 컨텍스트 port 호출
    }
}
