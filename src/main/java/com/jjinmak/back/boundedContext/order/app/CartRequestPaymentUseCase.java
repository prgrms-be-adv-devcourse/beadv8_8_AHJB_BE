package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.event.CartCreateOrderEvent;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static com.jjinmak.back.boundedContext.order.exception.CartErrorCode.*;

@Service
@RequiredArgsConstructor
public class CartRequestPaymentUseCase {

    private final OrderSupport orderSupport;
    private final CartItemRepository cartItemRepository;
    private final EventPublisher eventPublisher;

    /**
     * 낙찰자가 해당하는 상품을 보유하고 있는지 검사한 후, 주문 컨텍스트로 이벤트를 발행한다.
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

        List<CartItemDto> cartItemDtoList = cartItems.stream().map(CartItem::dto).toList();

        long totalPrice = cartItems.stream()
                .mapToLong(i -> i.getWinningPrice() + i.getDeliveryFee())
                .sum();

        CartCreateOrderEvent cartCreateOrderEvent = new CartCreateOrderEvent(totalPrice, cartItemDtoList);

        eventPublisher.publish(cartCreateOrderEvent);
    }
}
