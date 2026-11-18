package com.jjinmak.back.boundedContext.review.out;

import com.jjinmak.back.boundedContext.review.domain.ReviewMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewMemberRepository extends JpaRepository<ReviewMember, Long> {
}
