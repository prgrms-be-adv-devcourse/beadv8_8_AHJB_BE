package com.jjinmak.back.boundedContext.product.out;

import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMemberRepository extends JpaRepository<ProductMember, Long> {
}
