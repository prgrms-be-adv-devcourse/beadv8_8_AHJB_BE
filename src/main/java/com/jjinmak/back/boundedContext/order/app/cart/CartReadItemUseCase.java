package com.jjinmak.back.boundedContext.order.app.cart;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.CartMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.CartMemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CartReadItemUseCase {

    private final CartMemberRepository cartMemberRepository;
    private final CartItemRepository cartItemRepository;

    public List<CartItemDto> readWinnerItems(Long winnerId){
        CartMember winner = cartMemberRepository.findById(winnerId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        List<CartItem> items = cartItemRepository.findAllByWinnerOrderByWinAt(winner);

        return items.stream().map(CartItem::dto).toList();
    }
}
