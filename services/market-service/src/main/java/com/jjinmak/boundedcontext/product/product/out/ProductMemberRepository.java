package com.jjinmak.boundedcontext.product.product.out;

import com.jjinmak.boundedcontext.product.product.domain.ProductMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMemberRepository extends JpaRepository<ProductMember, Long> {
}
