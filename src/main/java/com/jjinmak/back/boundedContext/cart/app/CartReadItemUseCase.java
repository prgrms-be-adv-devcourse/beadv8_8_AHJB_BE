package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CartReadItemUseCase {

    private final CartMemberRepository cartMemberRepository;
    private final CartItemRepository cartItemRepository;

    public List<CartItemDto> readWinnerItems(UUID winnerId){
        CartMember winner = cartMemberRepository.findByUuid(winnerId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        List<CartItem> items = cartItemRepository.findAllByWinnerOrderByWinAt(winner);

        return items.stream().map(CartItem::dto).toList();
    }
}
