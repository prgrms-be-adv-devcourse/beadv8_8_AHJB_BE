package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.shared.out.MarketOutboxRecorder;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_FAILED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_STARTED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;

// 경매 시작·낙찰·유찰 이벤트를 market 아웃박스에 저장한다 (#548).
// 발행 즉시 트랜잭션에 모아 두고 커밋 직전에 한 번에 INSERT한다(MarketOutboxRecorder). 토픽과 키는 기존 발행기(auctionId)와 같다.
// 입찰 성공·마감 임박은 이번 범위가 아니라 AuctionKafkaEventPublisher에서 바로 보낸다.
@Component
@RequiredArgsConstructor
public class AuctionOutboxWriter {

    private final MarketOutboxRecorder recorder;

    @EventListener
    public void write(AuctionStartedEvent event){
        recorder.record(AUCTION_STARTED, event.auctionId(), event);
    }

    @EventListener
    public void write(AuctionWonEvent event){
        recorder.record(AUCTION_WON, event.auctionId(), event);
    }

    @EventListener
    public void write(AuctionFailedEvent event){
        recorder.record(AUCTION_FAILED, event.auctionId(), event);
    }
}
