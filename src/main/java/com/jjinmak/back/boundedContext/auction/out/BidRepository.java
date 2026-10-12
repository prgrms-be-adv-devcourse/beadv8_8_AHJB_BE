package com.jjinmak.back.boundedContext.auction.out;

import com.jjinmak.back.boundedContext.auction.domain.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid,Long> {
    //페이지단위 조회
    Page<Bid> findByAuctionId(Long auctionId, Pageable pageable);
}
