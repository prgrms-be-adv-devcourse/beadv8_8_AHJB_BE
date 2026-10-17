package com.jjinmak.back.boundedContext.order.app.cart;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.boundedContext.order.exception.CartErrorCode.*;

@Service
@RequiredArgsConstructor
public class CartPaymentSucceedUseCase {

    private final CartItemRepository cartItemRepository;

    public void paymentSucceed(Long auctionId){
        CartItem item = cartItemRepository.findByAuctionId(auctionId)
                .orElseThrow(() -> new BusinessException(CART_ITEM_NOT_FOUND));

        cartItemRepository.delete(item);
    }
}
