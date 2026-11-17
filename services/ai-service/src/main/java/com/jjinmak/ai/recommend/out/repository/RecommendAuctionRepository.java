package com.jjinmak.ai.recommend.out.repository;

import com.jjinmak.ai.recommend.domain.RecommendAuction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendAuctionRepository extends JpaRepository<RecommendAuction, Long> {
}
