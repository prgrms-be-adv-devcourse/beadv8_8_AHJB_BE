package com.jjinmak.auction.out;

import com.jjinmak.auction.domain.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid,Long> {
    //페이지단위 조회
    Page<Bid> findByAuctionId(Long auctionId, Pageable pageable);

    @Query("select distinct b.bidderId from Bid b where b.auctionId = :auctionId")
    List<Long> findDistinctBidderIds(@Param("auctionId") Long auctionId);
}
