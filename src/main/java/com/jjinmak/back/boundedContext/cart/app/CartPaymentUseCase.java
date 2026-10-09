package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.dto.CartCreateOrderDto;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.jjinmak.back.boundedContext.cart.exception.CartErrorCode.CART_ITEM_FORBIDDEN;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CartPaymentUseCase {

    private final CartMemberRepository cartMemberRepository;
    private final CartItemRepository cartItemRepository;

    /**
     * 낙찰자가 해당하는 상품을 보유하고 있는지 검사한 후, 주문 컨텍스트로 이벤트를 발행한다.
     * @param winnerId   - 조회의 주체가 되는 낙찰자
     * @param productIds - 결제를 시도하는 상품의 id 리스트
     */
    public void payment(UUID winnerId, List<Long> productIds){
        CartMember winner = cartMemberRepository.findByUuid(winnerId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        Set<Long> productIdSet = new HashSet<>(productIds);

        List<CartItem> cartItems = cartItemRepository.findAllByWinnerAndProductIdIn(winner, productIdSet);

        if (productIdSet.size() != cartItems.size()){
            throw new BusinessException(CART_ITEM_FORBIDDEN);
        }

        List<CartItemDto> cartItemDtoList = cartItems.stream().map(CartItem::dto).toList();

        long totalPrice = cartItems.stream()
                .mapToLong(i -> i.getWinningPrice() + i.getDeliveryFee())
                .sum();

        // TODO: 생성된 cartItems를 주문 컨텍스트로 전달
        CartCreateOrderDto cartCreateOrderDto = new CartCreateOrderDto(totalPrice, cartItemDtoList);
    }
}
