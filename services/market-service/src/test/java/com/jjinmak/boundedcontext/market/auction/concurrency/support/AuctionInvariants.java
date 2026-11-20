package com.jjinmak.boundedcontext.market.auction.concurrency.support;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.market.auction.domain.Bid;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.out.AuctionRepository;
import com.jjinmak.boundedcontext.market.auction.out.BidRepository;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// 락 방식과 무관하게 항상 지켜야 하는 것. 테스트는 "충돌 몇 건"이 아니라 이것만 단언한다.
public final class AuctionInvariants {

    private AuctionInvariants() {
    }

    public static List<String> checkEnglish(AuctionRepository auctionRepository, BidRepository bidRepository, Long auctionId) {
        List<String> violations = new ArrayList<>();
        EnglishAuction auction = (EnglishAuction) auctionRepository.findById(auctionId).orElseThrow();
        // 입찰 조회는 최신순 Slice만 있다(#515) → 오래된 순으로 뒤집어 금액 증가를 확인한다
        List<Bid> bids = new ArrayList<>(bidRepository.findByAuctionIdOrderByIdDesc(auctionId, PageRequest.of(0, 10_000)).getContent());
        Collections.reverse(bids);

        if (auction.getBidCount() != bids.size()) {
            violations.add("bidCount " + auction.getBidCount() + " != 입찰 행 " + bids.size());
        }
        for (int i = 1; i < bids.size(); i++) {
            Bid prev = bids.get(i - 1);
            Bid cur = bids.get(i);
            if (cur.getBidPrice() <= prev.getBidPrice()) {
                violations.add("금액 역전 bid " + prev.getId() + "(" + prev.getBidPrice() + ") → " + cur.getId() + "(" + cur.getBidPrice() + ")");
            }
            boolean instantWin = auction.getInstantWinPrice() != null && cur.getBidPrice().equals(auction.getInstantWinPrice());
            if (!instantWin && cur.getBidderId().equals(prev.getBidderId())) {
                violations.add("같은 회원 연속 입찰 " + cur.getBidderId());
            }
        }
        if (!bids.isEmpty()) {
            Bid top = bids.get(bids.size() - 1);
            if (!Objects.equals(auction.getHighestBidPrice(), top.getBidPrice())) {
                violations.add("최고가 " + auction.getHighestBidPrice() + " != 최고 행 " + top.getBidPrice());
            }
            if (!Objects.equals(auction.getHighestBidderId(), top.getBidderId())) {
                violations.add("최고 입찰자 " + auction.getHighestBidderId() + " != 최고 행 " + top.getBidderId());
            }
        }
        if (auction.getStatus() == AuctionStatus.WON) {
            if (!Objects.equals(auction.getWinnerId(), auction.getHighestBidderId())
                    || !Objects.equals(auction.getWinningPrice(), auction.getHighestBidPrice())) {
                violations.add("낙찰자·낙찰가가 최고 입찰과 다름 winner=" + auction.getWinnerId() + " price=" + auction.getWinningPrice());
            }
            if (auction.getWinAt() != null && !bids.isEmpty()
                    && bids.get(bids.size() - 1).getBidAt().isAfter(auction.getWinAt())) {
                violations.add("낙찰 이후 접수된 입찰");
            }
        }
        return violations;
    }
}
