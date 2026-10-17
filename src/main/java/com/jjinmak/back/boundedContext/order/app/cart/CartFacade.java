package com.jjinmak.back.boundedContext.order.app.cart;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.CartMember;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartFacade {

    private final CartSyncMemberUseCase cartSyncMemberUseCase;
    private final CartCreateCartItemUseCase cartCreateCartItemUseCase;
    private final CartReadItemUseCase cartReadItemUseCase;
    private final CartPaymentUseCase cartPaymentUseCase;
    private final CartPaymentSucceedUseCase cartPaymentSucceedUseCase;
    private final CartDefaultPaymentUseCase cartDefaultPaymentUseCase;

    @Transactional
    public CartMember syncMember(MemberDto memberDto){
        return cartSyncMemberUseCase.syncMember(memberDto);
    }

    @Transactional
    public CartItem createCartItem(AuctionWonEvent event){
        return cartCreateCartItemUseCase.createCartItem(event);
    }

    @Transactional(readOnly = true)
    public List<CartItemDto> readWinnerItems(Long winnerId){
        return cartReadItemUseCase.readWinnerItems(winnerId);
    }

    @Transactional
    public void paymentCartItems(Long winnerId, List<Long> productIds){
        cartPaymentUseCase.payment(winnerId, productIds);
    }

    @Transactional
    public void paymentSucceed(Long auctionId){
        cartPaymentSucceedUseCase.paymentSucceed(auctionId);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredItemIds(){
        return cartDefaultPaymentUseCase.findExpiredItemIds();
    }

    @Transactional
    public void deleteExpiredItem(Long itemId){
        cartDefaultPaymentUseCase.deleteExpiredItem(itemId);
    }
}
