package com.jjinmak.auction.out;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction,Long> {
    boolean existsByProductId(Long productId);

    @Query(
            """
            select a.id from Auction a where a.status =:status and a.endAt<= :now
                        order by a.endAt asc
            """
    )
    List<Long> findCloseTargetIds(@Param("status") AuctionStatus status,@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            select a from EnglishAuction a
            where a.status = :status
              and a.id in (select b.auctionId from Bid b where b.bidderId = :bidderId)
            order by a.endAt asc
            """)
    List<EnglishAuction> findMyBidding(@Param("bidderId") Long bidderId, @Param("status") AuctionStatus status);

    @Query("""
            select a from EnglishAuction a
            where a.status in :statuses
              and (:category is null or a.category = :category)
            """)
    Page<EnglishAuction> findEnglishList(@Param("statuses") Collection<AuctionStatus> statuses,
                                  @Param("category") String category,
                                  Pageable pageable);

    @Query("""
        select a from DutchAuction a
        where a.status in :statuses
        """)
    Page<DutchAuction> findDutchList(@Param("statuses") Collection<AuctionStatus> statuses, Pageable pageable);

    @Query("""
        select a.id from EnglishAuction a
        where a.status = :status
          and a.endingSoonNotifiedAt is null
          and a.bidCount > 0
          and a.endAt > :now and a.endAt <= :threshold
        order by a.endAt asc
        """)
    List<Long> findEndingSoonTargetIds(@Param("status") AuctionStatus status,
                                       @Param("now") LocalDateTime now,
                                       @Param("threshold") LocalDateTime threshold,
                                       Pageable pageable);

    //동시 입찰과 충돌하지 않고, 1회만 성공한다 조건부 UPDATE -> version을 +1 하지 않음
    @Modifying
    @Query("""
        update EnglishAuction a set a.endingSoonNotifiedAt = :now
        where a.id = :auctionId
          and a.endingSoonNotifiedAt is null
          and a.status = :status
          and a.endAt > :now
        """)
    int markEndingSoonNotified(@Param("auctionId") Long auctionId,
                               @Param("status") AuctionStatus status,
                               @Param("now") LocalDateTime now);
}
