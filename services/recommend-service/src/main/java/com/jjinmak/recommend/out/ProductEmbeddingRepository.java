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

    @Query(value = """
        SELECT a.id AS auctionId, a.product_id AS productId, p.category AS category,
               a.current_price AS currentPrice, a.end_at AS endAt
        FROM product_embedding base
        JOIN product_embedding e ON e.status = 'READY' AND e.model = :model
        JOIN recommend_product p ON p.id = e.id AND p.deleted = false
        JOIN recommend_auction a ON a.product_id = e.id
        WHERE base.id = :productId
          AND base.status = 'READY' AND base.model = :model
          AND a.id <> :auctionId
          AND a.status = 'ACTIVE' AND a.end_at > now()
        ORDER BY e.embedding <#> base.embedding
        LIMIT :size
        """, nativeQuery = true)
    List<AuctionRow> findSimilarAuctions(Long productId, Long auctionId, String model, int size);
}
