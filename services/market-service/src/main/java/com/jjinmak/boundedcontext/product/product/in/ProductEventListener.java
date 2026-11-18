package com.jjinmak.boundedcontext.product.product.in;

import com.jjinmak.boundedcontext.product.product.app.ProductFacade;
import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductFacade productFacade;

    // 회원 가입 시 회원 서비스가 보내는 이벤트로 판매자·찜·문의 작성자 정보를 복제해 둔다.
    // (같은 회원이 다시 와도 닉네임만 갱신한다)
    @KafkaListener(topics = MemberEventTopics.MEMBER_CREATED, groupId = "product")
    public void handle(MemberCreatedEvent event){
        productFacade.syncMember(event.member());
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
