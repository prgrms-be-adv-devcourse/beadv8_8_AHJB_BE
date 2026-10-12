package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import com.jjinmak.back.boundedContext.auction.domain.Bid;
import com.jjinmak.back.boundedContext.auction.dto.AuctionInstantWinResponseDto;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.boundedContext.auction.out.BidRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionInstantWinUseCase {
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;

    public AuctionInstantWinResponseDto instantWin(Long auctionId,Long bidderId){

        LocalDateTime now = LocalDateTime.now();
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(()->new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));
        // 공통검증
        validateInstantWin(auction,bidderId,now);
        auction.winInstantly(bidderId,now);
        bidRepository.save(new Bid(auction.getId(),bidderId, auction.getInstantWinPrice(), now));

        return new AuctionInstantWinResponseDto(
                auctionId,
                auction.getInstantWinPrice(),
                now,
                auction.getStatus()
        );
    }
    private void validateInstantWin(Auction auction, Long bidderId, LocalDateTime now) {
        if (auction.getStatus() != AuctionStatus.IN_PROGRESS) {
            throw new BusinessException(AuctionErrorCode.AUCTION_NOT_IN_PROGRESS);
        }
        if (auction.isEnded(now)) {
            throw new BusinessException(AuctionErrorCode.AUCTION_ALREADY_ENDED);
        }
        if (auction.getSellerId().equals(bidderId)) {
            throw new BusinessException(AuctionErrorCode.SELLER_CANNOT_BID);
        }
        if (auction.getInstantWinPrice() == null) {
            throw new BusinessException(AuctionErrorCode.INSTANT_WIN_NOT_AVAILABLE);
        }
    }
}
