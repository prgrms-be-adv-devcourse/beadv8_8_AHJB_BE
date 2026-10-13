package com.jjinmak.back.boundedContext.auction.out;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction,Long> {
    boolean existsByProductIdAndStatus(Long productId, AuctionStatus status);
}
