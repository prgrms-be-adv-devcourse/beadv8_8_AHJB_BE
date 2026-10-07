package com.jjinmak.back.boundedContext.auction.in;

import com.jjinmak.back.boundedContext.auction.app.AuctionFacade;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.shared.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionEventListener {

    private final AuctionFacade auctionFacade;

    //경매 생성이 실패하면 상품 등록도 함께 롤백된다.

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProductRegistered(ProductRegisteredEvent event) {
        AuctionCreateDto dto = new AuctionCreateDto(
                event.productId(),
                event.sellerId(),
                event.startPrice(),
                event.instantWinPrice(),
                event.duration()
        );
        auctionFacade.createAuction(dto);
    }
}