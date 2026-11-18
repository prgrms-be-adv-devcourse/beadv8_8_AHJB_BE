package com.jjinmak.boundedcontext.auction.app;

import com.jjinmak.boundedcontext.auction.domain.Auction;
import com.jjinmak.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.boundedcontext.auction.exception.AuctionErrorCode.AUCTION_NOT_RELISTABLE;

@Service
@RequiredArgsConstructor
public class AuctionRelistCheckUseCase {

    private final AuctionReader auctionReader;

    // 이전 회차 경매가 다음 회차로 다시 열 수 있는 상태인지 확인한다.
    public void validateRelistable(Long auctionId) {
        Auction auction = auctionReader.get(auctionId);
        if (!auction.isRelistable()) {
            throw new BusinessException(AUCTION_NOT_RELISTABLE);
        }
    }
}
