package com.jjinmak.product.product.out;

import com.jjinmak.product.product.domain.ProductMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMemberRepository extends JpaRepository<ProductMember, Long> {
}
