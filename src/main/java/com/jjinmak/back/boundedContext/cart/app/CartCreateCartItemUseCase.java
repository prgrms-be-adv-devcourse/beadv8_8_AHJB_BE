package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.exception.NotFoundException;
import com.jjinmak.back.shared.auction.dto.AuctionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartCreateCartItemUseCase {

    private final CartItemRepository cartItemRepository;
    private final CartMemberRepository cartMemberRepository;

    /**
     * 경매 완료 이벤트를 수신하고, 해당 상품을 CartItem으로 등록한다.
     * @param auctionDto - 낙찰자Id, 판매자Id, 낙찰가, 배송비, 낙찰 시각
     * @return CartItem - 생성된 CartItem
     */
    public CartItem createCartItem(AuctionDto auctionDto){
        UUID winnerId = auctionDto.winnerId();
        UUID sellerId = auctionDto.sellerId();

        CartMember winner = cartMemberRepository.findByUuid(winnerId)
                .orElseThrow(()->new NotFoundException("COMMON???", "존재하지 않는 회원입니다."));

        CartMember seller = cartMemberRepository.findByUuid(sellerId)
                .orElseThrow(()->new NotFoundException("COMMON???", "존재하지 않는 회원입니다."));

        Long productId = auctionDto.productId();
        Long winningPrice = auctionDto.winningPrice();
        Long deliveryFee = auctionDto.deliveryFee();

        LocalDateTime winAt = auctionDto.winAt();

        CartItem item = new CartItem(winner, seller, productId, winningPrice, deliveryFee, winAt);

        return cartItemRepository.save(item);
    }
}
