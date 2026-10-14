package com.jjinmak.back.boundedContext.auction.out;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.auction.event.AuctionBidUpdatedEvent;
import com.jjinmak.back.shared.auction.event.AuctionFailedEvent;
import com.jjinmak.back.shared.auction.event.AuctionStartedEvent;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AuctionEventPublisher {
    private final EventPublisher eventPublisher;

    public void publishWon(Auction auction){
        eventPublisher.publish(new AuctionWonEvent(
                auction.getId(),
                auction.getProductId(),
                auction.getWinnerId(),
                auction.getSellerId(),
                auction.getWinningPrice(),
                auction.getShippingFee(),
                auction.getWinAt()
        ));
    }
    public void publishFailed(Auction auction, LocalDateTime failedAt){
        eventPublisher.publish(new AuctionFailedEvent(
                auction.getId(),
                auction.getProductId(),
                auction.getFailureReason().name(),
                failedAt
        ));
    }

    public void publishStarted(Auction auction){
        eventPublisher.publish(new AuctionStartedEvent(
                auction.getId(),
                auction.getProductId(),
                auction.getStartPrice(),
                auction.getInstantWinPrice(),
                auction.getStartAt(),
                auction.getEndAt()
        ));
    }
    public void publishBidUpdated(Auction auction) {
        eventPublisher.publish(new AuctionBidUpdatedEvent(
                auction.getId(),
                auction.getProductId(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                auction.getEndAt()
        ));
    }

}
