package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductFacade;
import com.jjinmak.back.shared.auction.event.AuctionStartedEvent;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductFacade productFacade;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(MemberCreatedEvent event){
        productFacade.syncMember(event.member());
    }

    // 경매가 시작되면 상품에 현재 회차의 경매 id를 기록한다.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(AuctionStartedEvent event){
        productFacade.startAuction(event);
    }
}
