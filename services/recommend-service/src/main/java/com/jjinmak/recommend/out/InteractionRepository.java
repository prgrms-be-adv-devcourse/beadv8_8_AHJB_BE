package com.jjinmak.recommend.out;

import com.jjinmak.recommend.domain.Interaction;
import com.jjinmak.recommend.domain.InteractionType;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.domain.RecommendProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    boolean existsByMemberAndProductAndType(RecommendMember member, RecommendProduct product, InteractionType type);
}
