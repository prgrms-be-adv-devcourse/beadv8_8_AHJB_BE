package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.Bid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid,Long> {
    // 경매별 최신순 입찰 내역. idx_bid_auction_id (auction_id, id DESC)를 타고,
    // Slice라 size+1건만 읽어 hasNext를 판단한다(전체 개수 count 쿼리 없음). 전체 개수는 경매의 bidCount를 쓴다
    Slice<Bid> findByAuctionIdOrderByIdDesc(Long auctionId, Pageable pageable);

    @Query("select distinct b.bidderId from Bid b where b.auctionId = :auctionId")
    List<Long> findDistinctBidderIds(@Param("auctionId") Long auctionId);
}
