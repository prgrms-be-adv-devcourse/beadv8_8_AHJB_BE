package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.shared.domain.AuctionType;
import com.jjinmak.boundedcontext.market.auction.domain.*;
import com.jjinmak.boundedcontext.market.auction.out.eventPublisher.EventPublisher;
import com.jjinmak.contracts.auction.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
    // 일괄 낙찰처럼 엔티티를 읽지 않고 처리한 경우 (#548)
    public void publishWon(Long auctionId, Long productId, Long winnerId, Long sellerId,
                           Long winningPrice, Long shippingFee, LocalDateTime winAt){
        eventPublisher.publish(new AuctionWonEvent(auctionId, productId, winnerId, sellerId, winningPrice, shippingFee, winAt));
    }

    public void publishFailed(Auction auction, LocalDateTime failedAt){
        publishFailed(auction.getId(), auction.getProductId(), auction.getSellerId(), auction.getFailureReason(), failedAt);
    }

    // 일괄 유찰처럼 엔티티를 읽지 않고 처리한 경우 (#516)
    public void publishFailed(Long auctionId, Long productId, Long sellerId, FailureReason reason, LocalDateTime failedAt){
        eventPublisher.publish(new AuctionFailedEvent(
                auctionId,
                productId,
                reason.name(),
                failedAt,
                sellerId
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

    public void publishBidSucceed(EnglishAuction auction, Bid bid, Long previousHighestBidderId) {
        eventPublisher.publish(new AuctionBidSucceedEvent(
                bid.getId(),
                auction.getId(),
                auction.getProductId(),
                bid.getBidderId(),
                bid.getBidPrice(),
                auction.getBidCount(),
                auction.getEndAt(),
                bid.getBidAt(),
                previousHighestBidderId
        ));
    }

    public void publishEndingSoon(EnglishAuction auction, List<Long> bidderIds, LocalDateTime now) {
        eventPublisher.publish(new AuctionEndingSoonEvent(
                auction.getId(),
                auction.getProductId(),
                auction.getEndAt(),
                bidderIds,
                now
        ));
    }
}
