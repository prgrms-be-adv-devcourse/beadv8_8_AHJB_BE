package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.exception.ForbiddenException;
import com.jjinmak.back.global.exception.NotFoundException;
import com.jjinmak.back.shared.cart.dto.CartCreateOrderDto;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

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
                .orElseThrow(() -> new NotFoundException("COMMON103", "존재하지 않는 회원입니다."));

        Set<Long> productIdSet = new HashSet<>(productIds);

        List<CartItem> cartItems = cartItemRepository.findAllByWinnerAndProductIdIn(winner, productIdSet);

        if (productIdSet.size() != cartItems.size()){
            throw new ForbiddenException("CART001", "보유하지 않는 상품이 선택되었습니다.");
        }

        List<CartItemDto> cartItemDtoList = cartItems.stream().map(CartItem::dto).toList();

        long totalPrice = cartItems.stream()
                .mapToLong(i -> i.getWinningPrice() + i.getDeliveryFee())
                .sum();

        // TODO: 생성된 cartItems를 주문 컨텍스트로 전달
        CartCreateOrderDto cartCreateOrderDto = new CartCreateOrderDto(totalPrice, cartItemDtoList);
    }
}
