package com.jjinmak.chat.in;

import com.jjinmak.chat.app.ChatFacade;
import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatFacade chatFacade;

    @KafkaListener(topics = AuctionEventTopics.AUCTION_WON, groupId = "chat")
    public void handle(AuctionWonEvent event) {
        chatFacade.createRoom(event);
    }

    @KafkaListener(topics = MemberEventTopics.MEMBER_CREATED, groupId = "chat")
    public void handle(MemberCreatedEvent event) {
        chatFacade.saveMember(event.member());
    }

    @KafkaListener(topics = ProductEventTopics.PRODUCT_REGISTERED, groupId = "chat")
    public void handle(ProductRegisteredEvent event) {
        chatFacade.saveProduct(event);
    }
}