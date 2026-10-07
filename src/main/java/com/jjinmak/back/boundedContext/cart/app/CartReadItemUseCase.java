package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.exception.NotFoundException;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartReadItemUseCase {

    private final CartMemberRepository cartMemberRepository;
    private final CartItemRepository cartItemRepository;

    public List<CartItemDto> readWinnerItems(UUID winnerId){
        CartMember winner = cartMemberRepository.findByUuid(winnerId)
                .orElseThrow(() -> new NotFoundException("COMMON103", "존재하지 않는 회원입니다."));

        List<CartItem> items = cartItemRepository.findAllByWinnerOrderByWinAt(winner);

        return items.stream().map(CartItem::dto).toList();
    }
}
