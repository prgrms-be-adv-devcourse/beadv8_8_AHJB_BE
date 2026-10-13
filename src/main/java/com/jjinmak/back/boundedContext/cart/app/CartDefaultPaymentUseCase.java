package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.event.CartPaymentDefaultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.back.boundedContext.cart.exception.CartErrorCode.CART_ITEM_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CartDefaultPaymentUseCase {

    private final EventPublisher eventPublisher;

    private final CartItemRepository cartItemRepository;

    public List<Long> findExpiredItemIds(){
        return cartItemRepository.findExpiredItemIds(LocalDateTime.now().minusHours(1));
    }

    public void deleteExpiredItem(Long id){
        CartItem item = cartItemRepository.findByIdWithParticipant(id)
                .orElseThrow(() -> new BusinessException(CART_ITEM_NOT_FOUND));

        cartItemRepository.delete(item);

        CartPaymentDefaultEvent event = new CartPaymentDefaultEvent(item.getProductId(), item.getWinner().getId(), item.getSeller().getId(), item.getWinningPrice(), item.getDeliveryFee());

        eventPublisher.publish(event);
    }
}
