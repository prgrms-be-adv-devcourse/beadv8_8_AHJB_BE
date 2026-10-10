package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.domain.Bid;
import com.jjinmak.back.boundedContext.auction.domain.BidIncrement;
import com.jjinmak.back.boundedContext.auction.dto.AuctionBidResponseDto;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.boundedContext.auction.out.BidRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionBidUseCase {
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    public AuctionBidResponseDto bid(Long auctionId, Long bidderId, Long bidPrice){
        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(()->new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));
        // 공통검증
        validateBid(auction,bidderId,now);
        // 즉시낙찰 저장
        if(auction.getInstantWinPrice()!=null && bidPrice>= auction.getInstantWinPrice()){
            auction.winInstantly(bidderId,now);
            Bid bid = bidRepository.save(new Bid(auction.getId(),bidderId, auction.getInstantWinPrice(), now));
            return new AuctionBidResponseDto(
                    bid.getId(),
                    bidPrice,
                    auction.getInstantWinPrice(),
                    null,
                    auction.getEndAt(),
                    false,
                    true
            );
        }

        // 금액 검증
        validatePrice(auction,bidPrice);
        // 입찰
        auction.updateHighestBid(bidderId,bidPrice);
        // 연장여부 -> 5분 연장
        boolean extended = !now.isBefore(auction.getEndAt().minus(Duration.ofMinutes(5)));
        if(extended){
            auction.extendEndAt(now.plus(Duration.ofMinutes(5)));
        }
        // 입찰 저장
        Bid bid = bidRepository.save(new Bid(auction.getId(),bidderId,bidPrice,now));
        return new AuctionBidResponseDto(
                bid.getId(),
                bidPrice,
                auction.getCurrentPrice(),
                auction.getMinBidPrice(),
                auction.getEndAt(),
                extended,
                false
        );
    }
    // validateBid - 진행중,종료전,판매자아님,최고입찰자아님
    private void validateBid(Auction auction,Long bidderId,LocalDateTime now){
        if(auction.getStatus()!= AuctionStatus.IN_PROGRESS){
            throw new BusinessException(AuctionErrorCode.AUCTION_NOT_IN_PROGRESS);
        }
        if(auction.isEnded(now)){
            throw new BusinessException(AuctionErrorCode.AUCTION_ALREADY_ENDED);
        }
        if(auction.getSellerId().equals(bidderId)){
            throw new BusinessException(AuctionErrorCode.SELLER_CANNOT_BID);
        }
        if(bidderId.equals(auction.getHighestBidderId())){
            throw new BusinessException(AuctionErrorCode.ALREADY_HIGHEST_BIDDER);
        }
    }
    // validatePrice - 최소입찰이상인지, 단위 배수인지
    private void validatePrice(Auction auction,Long bidPrice){
        if(bidPrice< auction.getMinBidPrice()){
            throw new BusinessException(AuctionErrorCode.BID_PRICE_TOO_LOW);
        }
        if((bidPrice- auction.getCurrentPrice())% BidIncrement.unitOf(auction.getCurrentPrice())!=0){
            throw new BusinessException(AuctionErrorCode.INVALID_BID_UNIT);
        }
    }
}
