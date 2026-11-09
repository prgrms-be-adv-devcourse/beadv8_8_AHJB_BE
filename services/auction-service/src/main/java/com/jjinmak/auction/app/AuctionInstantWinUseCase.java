package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.Bid;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.AuctionInstantWinResponseDto;
import com.jjinmak.auction.exception.AuctionErrorCode;
import com.jjinmak.auction.out.AuctionRepository;
import com.jjinmak.auction.out.BidRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionInstantWinUseCase {
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionEventPublisher auctionEventPublisher;

    public AuctionInstantWinResponseDto instantWin(Long auctionId,Long bidderId, LocalDateTime now){

        EnglishAuction auction = auctionRepository.findById(auctionId)
                .filter(EnglishAuction.class::isInstance)
                .map(EnglishAuction.class::cast)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));
        // 공통검증
        validateInstantWin(auction,bidderId,now);
        auction.winInstantly(bidderId,now);
        bidRepository.save(new Bid(auction.getId(),bidderId, auction.getInstantWinPrice(), now));
        // 낙찰 이벤트발행
        auctionEventPublisher.publishWon(auction);
        return new AuctionInstantWinResponseDto(
                auctionId,
                auction.getInstantWinPrice(),
                now,
                auction.getStatus()
        );
    }
    private void validateInstantWin(EnglishAuction auction, Long bidderId, LocalDateTime now) {
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
