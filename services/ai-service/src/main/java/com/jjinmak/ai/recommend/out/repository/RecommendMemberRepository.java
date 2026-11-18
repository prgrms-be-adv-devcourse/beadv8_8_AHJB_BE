package com.jjinmak.ai.recommend.out.repository;

import com.jjinmak.ai.recommend.domain.RecommendMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendMemberRepository extends JpaRepository<RecommendMember, Long> {
}
