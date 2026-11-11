package com.jjinmak.recommend.out;

import com.jjinmak.recommend.domain.EmbeddingStatus;
import com.jjinmak.recommend.domain.ProductEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE product_embedding SET embedding = CAST(:embedding AS vector) WHERE id = :id",
            nativeQuery = true)
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    @Query("select e.id from ProductEmbedding e where e.status = :status or e.model != :model")
    List<Long> findAllByStatusAndModel(EmbeddingStatus status, String model);
}
