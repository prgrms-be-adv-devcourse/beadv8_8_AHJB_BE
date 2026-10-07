package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.shared.auction.dto.AuctionDto;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartFacade {

    private final CartSyncMemberUseCase cartSyncMemberUseCase;
    private final CartCreateCartItemUseCase cartCreateCartItemUseCase;
    private final CartReadItemUseCase cartReadItemUseCase;

    @Transactional
    public CartMember syncMember(MemberDto memberDto){
        return cartSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public CartItem createCartItem(AuctionDto auctionDto){
        return cartCreateCartItemUseCase.createCartItem(auctionDto);
    }

    @Transactional(readOnly = true)
    public List<CartItemDto> readWinnerItems(UUID winnerId){
        return cartReadItemUseCase.readWinnerItems(winnerId);
    }

}
