package com.jjinmak.recommend.in;

import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.app.RecommendFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.*;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_CHANGED;

@Component
@RequiredArgsConstructor
public class RecommendKafkaListener {

    private final RecommendFacade recommendFacade;
    private final String groupId = "recommend";

    @KafkaListener(topics = PRODUCT_CHANGED, groupId = groupId)
    public void handle(ProductChangedEvent event){
        recommendFacade.syncProduct(event);
    }

    @KafkaListener(topics = AUCTION_STARTED, groupId = groupId)
    public void handle(AuctionStartedEvent event){
        recommendFacade.startAuction(event);
    }

    @KafkaListener(topics = AUCTION_WON, groupId = groupId)
    public void handle(AuctionWonEvent event){
        recommendFacade.winAuction(event);
    }

    @KafkaListener(topics = AUCTION_FAILED, groupId = groupId)
    public void handle(AuctionFailedEvent event){
        recommendFacade.failAuction(event);
    }

    @KafkaListener(topics = AUCTION_BID_SUCCEED, groupId = groupId)
    public void handle(AuctionBidSucceedEvent event) {
        recommendFacade.bidAuction(event);
    }
}
