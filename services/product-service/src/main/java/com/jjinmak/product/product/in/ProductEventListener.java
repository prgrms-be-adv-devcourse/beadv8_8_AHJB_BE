package com.jjinmak.product.product.in;

import com.jjinmak.product.product.app.ProductFacade;
import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductFacade productFacade;

    // 회원 컨텍스트가 아직 Kafka로 발행하지 않아 Spring 이벤트로 받는다.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(MemberCreatedEvent event){
        productFacade.syncMember(event.member());
    }

    // 경매가 시작되면 상품에 현재 회차의 경매 id와 진행 중 상태를 기록한다.
    @KafkaListener(topics = AuctionEventTopics.AUCTION_STARTED, groupId = "product")
    public void handle(AuctionStartedEvent event){
        productFacade.startAuction(event);
    }

    // 낙찰되면 상품의 경매 상태를 낙찰로 바꾼다.
    @KafkaListener(topics = AuctionEventTopics.AUCTION_WON, groupId = "product")
    public void handle(AuctionWonEvent event){
        productFacade.markAuctionSold(event);
    }

    // 무산되면(유찰·미결제·환불) 상품의 경매 상태를 무산으로 바꾸고 사유를 남긴다.
    @KafkaListener(topics = AuctionEventTopics.AUCTION_FAILED, groupId = "product")
    public void handle(AuctionFailedEvent event){
        productFacade.markAuctionFailed(event);
    }
}
