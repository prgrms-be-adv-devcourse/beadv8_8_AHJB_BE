package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.shared.auction.dto.AuctionDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartFacade {

    private final CartSyncMemberUseCase cartSyncMemberUseCase;
    private final CartCreateCartItemUseCase cartCreateCartItemUseCase;

    @Transactional
    public CartMember syncMember(MemberDto memberDto){
        return cartSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public CartItem createCartItem(AuctionDto auctionDto){
        return cartCreateCartItemUseCase.createCartItem(auctionDto);
    }

}
