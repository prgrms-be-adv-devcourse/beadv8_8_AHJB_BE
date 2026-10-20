package com.jjinmak.back.boundedContext.auction.out;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction,Long> {
    boolean existsByProductIdAndStatus(Long productId, AuctionStatus status);

    @Query(
            """
            select a.id from Auction a where a.status =:status and a.endAt<= :now
                        order by a.endAt asc
            """
    )
    List<Long> findCloseTargetIds(@Param("status") AuctionStatus status,@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            select a from Auction a
            where a.status = :status
              and a.id in (select b.auctionId from Bid b where b.bidderId = :bidderId)
            order by a.endAt asc
            """)
    List<Auction> findMyBidding(@Param("bidderId") Long bidderId, @Param("status") AuctionStatus status);

    @Query("""
            select a from Auction a
            where a.status in :statuses
              and (:category is null or a.productSnapshot.category = :category)
            """)
    Page<Auction> findList(@Param("statuses") Collection<AuctionStatus> statuses,
                           @Param("category") String category,
                           Pageable pageable);
}
