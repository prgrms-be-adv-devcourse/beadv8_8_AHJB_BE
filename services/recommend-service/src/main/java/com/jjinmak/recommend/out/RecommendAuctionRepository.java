package com.jjinmak.recommend.out;

import com.jjinmak.recommend.domain.RecommendAuction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendAuctionRepository extends JpaRepository<RecommendAuction, Long> {
}
