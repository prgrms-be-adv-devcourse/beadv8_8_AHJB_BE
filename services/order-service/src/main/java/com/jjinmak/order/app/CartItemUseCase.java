package com.jjinmak.order.app;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.cart.dto.CartItemDto;
import com.jjinmak.order.domain.CartItem;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.out.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartItemUseCase {

    private final OrderSupport orderSupport;
    private final CartItemRepository cartItemRepository;

    /**
     * 경매 완료 이벤트를 수신하고, 해당 상품을 CartItem으로 등록한다.
     * @param event - 낙찰자Id, 판매자Id, 낙찰가, 배송비, 낙찰 시각
     */
    public void createCartItem(AuctionWonEvent event){
        Long winnerId = event.winnerId();
        Long sellerId = event.sellerId();

        OrderMember winner = orderSupport.getMember(winnerId);

        OrderMember seller = orderSupport.getMember(sellerId);

        Long auctionId = event.auctionId();
        Long productId = event.productId();
        Long winningPrice = event.winningPrice();
        Long deliveryFee = event.shippingFee();

        LocalDateTime winAt = event.winAt();

        CartItem item = new CartItem(winner, seller, auctionId, productId, winningPrice, deliveryFee, winAt);

        cartItemRepository.save(item);
    }

    public List<CartItemDto> readWinnerItems(Long winnerId){
        OrderMember winner = orderSupport.getMember(winnerId);

        List<CartItem> items = cartItemRepository.findAllByWinnerOrderByWinAt(winner);

        return items.stream().map(CartItem::dto).toList();
    }
}
