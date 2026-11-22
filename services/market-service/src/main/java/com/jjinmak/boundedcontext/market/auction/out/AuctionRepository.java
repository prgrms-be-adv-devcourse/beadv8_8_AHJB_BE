package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.dto.AuctionDeadlineDto;

import com.jjinmak.boundedcontext.market.auction.domain.Auction;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.market.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction,Long> {
    // 같은 상품의 해당 회차 경매 (product_id, round 유니크)
    Optional<Auction> findByProductIdAndRound(Long productId, int round);

    // 일괄 열기의 중복 확인용: 여러 상품의 경매를 한 번에 (회차는 호출하는 쪽에서 거른다)
    List<Auction> findByProductIdIn(Collection<Long> productIds);

    @Query(
            """
            select a.id from Auction a where a.status =:status and a.endAt<= :now
                        order by a.endAt asc
            """
    )
    List<Long> findCloseTargetIds(@Param("status") AuctionStatus status,@Param("now") LocalDateTime now, Pageable pageable);

    // 종료 타이머 미리 읽기 (#548 4차): until 전에 끝나는(이미 끝난 것 포함) 진행 중 경매의 id·종료 시각. idx_auction_status_end_at 사용
    @Query("""
            select new com.jjinmak.boundedcontext.market.auction.dto.AuctionDeadlineDto(a.id, a.endAt)
            from Auction a
            where a.status = :status and a.endAt <= :until
            order by a.endAt asc
            """)
    List<AuctionDeadlineDto> findDeadlinesBefore(
            @Param("status") AuctionStatus status, @Param("until") LocalDateTime until, Pageable pageable);

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
