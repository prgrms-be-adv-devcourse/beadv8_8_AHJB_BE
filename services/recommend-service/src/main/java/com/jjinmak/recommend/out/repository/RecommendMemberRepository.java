package com.jjinmak.recommend.out.repository;

import com.jjinmak.recommend.domain.RecommendMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendMemberRepository extends JpaRepository<RecommendMember, Long> {
}
