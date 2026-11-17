package com.jjinmak.product.review.out;

import com.jjinmak.product.review.domain.ReviewMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewMemberRepository extends JpaRepository<ReviewMember, Long> {
}
