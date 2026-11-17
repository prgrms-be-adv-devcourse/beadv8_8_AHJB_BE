package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.out.AuctionRepository;
import com.jjinmak.auction.out.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionEndingSoonUseCase {
    public static final Duration ENDING_SOON_WINDOW = Duration.ofMinutes(10);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionReader auctionReader;
    private final AuctionEventPublisher auctionEventPublisher;

    public List<Long> findTargetIds(LocalDateTime now, int batchSize) {
        return auctionRepository.findEndingSoonTargetIds(
                AuctionStatus.IN_PROGRESS, now, now.plus(ENDING_SOON_WINDOW), PageRequest.of(0, batchSize));
    }

    // 표시에 성공한 경우에만 발행한다. 이미 보냈거나 그사이 종료됐으면 false
    public boolean notifyEndingSoon(Long auctionId, LocalDateTime now) {
        int marked = auctionRepository.markEndingSoonNotified(auctionId, AuctionStatus.IN_PROGRESS, now);
        if (marked == 0) {
            return false;
        }
        EnglishAuction auction = auctionReader.getEnglish(auctionId);
        List<Long> bidderIds = bidRepository.findDistinctBidderIds(auctionId);
        auctionEventPublisher.publishEndingSoon(auction, bidderIds, now);
        return true;
    }
}
