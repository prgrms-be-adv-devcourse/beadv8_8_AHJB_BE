package com.jjinmak.recommend.out;

import com.jjinmak.recommend.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    boolean existsByMemberAndAuctionAndType(RecommendMember member, RecommendAuction auction, InteractionType type);

    Optional<Interaction> findByMemberAndAuctionAndType(RecommendMember member, RecommendAuction auction, InteractionType type);
}
