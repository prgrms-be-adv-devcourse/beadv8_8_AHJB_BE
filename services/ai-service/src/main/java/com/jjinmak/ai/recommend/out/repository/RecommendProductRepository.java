package com.jjinmak.ai.recommend.out.repository;

import com.jjinmak.ai.recommend.domain.RecommendProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RecommendProductRepository extends JpaRepository<RecommendProduct, Long> {

    @Query("select distinct p.category from RecommendProduct p where p.deleted = false order by p.category")
    List<String> findDistinctCategories();

    @Query("select distinct p.manufacturer from RecommendProduct p where p.deleted = false order by p.manufacturer")
    List<String> findDistinctManufacturers();
}
