package com.jjinmak.recommend.out.repository;

import com.jjinmak.recommend.domain.RecommendProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendProductRepository extends JpaRepository<RecommendProduct, Long> {
}
