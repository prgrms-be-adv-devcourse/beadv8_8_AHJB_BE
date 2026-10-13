package com.jjinmak.back.boundedContext.auction.out;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

}
