package com.jjinmak.recommend.out.repository;

import com.jjinmak.recommend.domain.InteractionLog;
import com.jjinmak.recommend.domain.InteractionType;
import com.jjinmak.recommend.domain.RecommendAuction;
import com.jjinmak.recommend.domain.RecommendMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, Long> {

    boolean existsByMemberAndAuctionAndTypeAndLastOccurredAt(RecommendMember member, RecommendAuction auction, InteractionType type, LocalDateTime occurredAt);
}
