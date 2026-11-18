package com.jjinmak.ai.recommend.out.repository;

import com.jjinmak.ai.recommend.domain.InteractionLog;
import com.jjinmak.ai.recommend.domain.InteractionType;
import com.jjinmak.ai.recommend.domain.RecommendAuction;
import com.jjinmak.ai.recommend.domain.RecommendMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, Long> {

    boolean existsByMemberAndAuctionAndTypeAndLastOccurredAt(RecommendMember member, RecommendAuction auction, InteractionType type, LocalDateTime occurredAt);
}
