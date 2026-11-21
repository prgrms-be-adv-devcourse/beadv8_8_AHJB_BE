package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuctionOutboxRepository extends JpaRepository<AuctionOutbox, Long> {

    List<AuctionOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();

    // 묶음 발행 표시 (#548): 행마다 조회·수정하지 않고 UPDATE 한 번
    @Modifying(clearAutomatically = true)
    @Query("update AuctionOutbox o set o.publishedAt = :publishedAt, o.updatedAt = :publishedAt where o.id in :ids")
    int markPublished(@Param("ids") List<Long> ids, @Param("publishedAt") LocalDateTime publishedAt);
}
