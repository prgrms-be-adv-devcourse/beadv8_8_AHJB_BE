package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.AuctionType;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.out.eventPublisher.EventPublisher;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
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
        boolean dutch = auction instanceof DutchAuction;
        Long instantWinPrice = (auction instanceof EnglishAuction english) ? english.getInstantWinPrice() : null;

        eventPublisher.publish(new AuctionStartedEvent(
                auction.getId(),
                auction.getProductId(),
                auction.getStartPrice(),
                instantWinPrice,
                auction.getStartAt(),
                dutch ? null : auction.getEndAt(),
                dutch ? AuctionType.DUTCH.name() : AuctionType.ENGLISH.name()
        ));
    }

}
