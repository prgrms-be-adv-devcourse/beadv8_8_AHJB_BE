package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.out.AuctionRepository;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import static com.jjinmak.auction.exception.AuctionErrorCode.*;

@Component
@RequiredArgsConstructor
public class AuctionReader {
    private final AuctionRepository auctionRepository;

    public Auction get(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_FOUND));
    }

    public EnglishAuction getEnglish(Long auctionId) {
        if (get(auctionId) instanceof EnglishAuction auction) {
            return auction;
        }
        throw new BusinessException(AUCTION_TYPE_MISMATCH);
    }

    public DutchAuction getDutch(Long auctionId) {
        if (get(auctionId) instanceof DutchAuction auction) {
            return auction;
        }
        throw new BusinessException(AUCTION_TYPE_MISMATCH);
    }
}
