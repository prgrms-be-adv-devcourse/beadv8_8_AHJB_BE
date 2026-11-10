package com.jjinmak.recommend.out;

import com.jjinmak.recommend.domain.RecommendProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendProductRepository extends JpaRepository<RecommendProduct, Long> {
}
