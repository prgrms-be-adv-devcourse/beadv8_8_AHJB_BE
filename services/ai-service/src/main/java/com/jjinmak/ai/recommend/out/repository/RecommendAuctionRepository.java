package com.jjinmak.ai.recommend.out.repository;

import com.jjinmak.ai.recommend.domain.RecommendAuction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RecommendAuctionRepository extends JpaRepository<RecommendAuction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RecommendAuction a WHERE a.id = :auctionId")
    Optional<RecommendAuction> findByIdForUpdate(Long auctionId);
}
