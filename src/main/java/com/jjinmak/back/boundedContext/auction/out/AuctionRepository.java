package com.jjinmak.back.boundedContext.auction.out;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction,Long> {
    // 시작할 경매 찾기
    List<Auction> findByStatusAndStartAtLessThanEqual(AuctionStatus status, LocalDateTime now);
    // 해당 상품의 가장 최근 경매 찾기
    Optional<Auction> findTopByProductIdOrderByRoundDesc(Long productId);

    boolean existsByProductId(Long productId);
}
