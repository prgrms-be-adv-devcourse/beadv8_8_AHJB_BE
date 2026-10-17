package com.jjinmak.back.boundedContext.order.app.cart;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.CartMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.CartMemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CartCreateCartItemUseCase {

    private final CartItemRepository cartItemRepository;
    private final CartMemberRepository cartMemberRepository;

    /**
     * 경매 완료 이벤트를 수신하고, 해당 상품을 CartItem으로 등록한다.
     * @param event - 낙찰자Id, 판매자Id, 낙찰가, 배송비, 낙찰 시각
     * @return CartItem - 생성된 CartItem
     */
    public CartItem createCartItem(AuctionWonEvent event){
        Long winnerId = event.winnerId();
        Long sellerId = event.sellerId();

        CartMember winner = cartMemberRepository.findById(winnerId)
                .orElseThrow(()->new BusinessException(USER_NOT_FOUND));

        CartMember seller = cartMemberRepository.findById(sellerId)
                .orElseThrow(()->new BusinessException(USER_NOT_FOUND));

        Long auctionId = event.auctionId();
        Long productId = event.productId();
        Long winningPrice = event.winningPrice();
        Long deliveryFee = event.shippingFee();

        LocalDateTime winAt = event.winAt();

        CartItem item = new CartItem(winner, seller, auctionId, productId, winningPrice, deliveryFee, winAt);

        return cartItemRepository.save(item);
    }
}
