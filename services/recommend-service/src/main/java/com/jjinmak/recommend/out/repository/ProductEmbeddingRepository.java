package com.jjinmak.recommend.out.repository;

import com.jjinmak.recommend.domain.EmbeddingStatus;
import com.jjinmak.recommend.domain.ProductEmbedding;
import com.jjinmak.recommend.out.row.AuctionRow;
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
        WITH scored AS MATERIALIZED (
            SELECT a.id, a.product_id, p.category, a.current_price, a.end_at,
                   -(e.embedding <#> base.embedding) AS similarity,
                   COALESCE(s.score, 0) AS cf_score,
                   CASE WHEN s.product_id IS NULL THEN 0
                        ELSE LEAST(1, (SELECT count(DISTINCT r.member_id)
                                       FROM interaction r
                                       WHERE r.product_id = e.id AND r.active = true) / 5.0)
                   END AS confidence
            FROM product_embedding base
            JOIN product_embedding e ON e.status = 'READY' AND e.model = :model
            JOIN recommend_product p ON p.id = e.id AND p.deleted = false
            JOIN recommend_auction a ON a.product_id = e.id
            LEFT JOIN product_cf_similarity s ON s.product_id = e.id AND s.other_product_id = base.id
            WHERE base.id = :productId
              AND base.status = 'READY' AND base.model = :model
              AND a.id <> :auctionId
              AND a.status = 'ACTIVE' AND a.end_at > now()
        )
        SELECT id AS auctionId, product_id AS productId, category AS category,
               current_price AS currentPrice, end_at AS endAt
        FROM scored
        ORDER BY (1 - 0.3 * confidence) * similarity + 0.3 * confidence * cf_score DESC
        LIMIT :size
        """, nativeQuery = true)
    List<AuctionRow> findSimilarAuctions(Long productId, Long auctionId, String model, int size);

    @Query(value = """
        WITH history AS (
            SELECT h.product_id, h.weight
            FROM unnest(CAST(:productIds AS bigint[]), CAST(:weights AS float8[])) AS h(product_id, weight)
        ),
        candidate AS (
            SELECT a.id, a.product_id, p.category, a.current_price, a.end_at,
                   count(i.id) AS bidder_count
            FROM recommend_auction a
            JOIN recommend_product p ON p.id = a.product_id AND p.deleted = false
            LEFT JOIN interaction i ON i.auction_id = a.id AND i.type = 'BID'
            WHERE a.status = 'ACTIVE' AND a.end_at > now()
              AND p.seller_id <> :memberId
              AND NOT EXISTS (
                  SELECT 1 FROM interaction m
                  WHERE m.member_id = :memberId AND m.auction_id = a.id
                    AND m.type IN ('BID', 'WISH') AND m.active = true
              )
            GROUP BY a.id, p.id
        ),
        taste AS (
            SELECT c.id, max(h.weight * -(e.embedding <#> b.embedding)) AS taste_score
            FROM candidate c
            JOIN product_embedding e ON e.id = c.product_id AND e.status = 'READY' AND e.model = :model
            CROSS JOIN history h
            JOIN product_embedding b ON b.id = h.product_id AND b.status = 'READY' AND b.model = :model
            GROUP BY c.id
        ),
        collaboration AS (
            SELECT c.id, max(h.weight * s.score) AS cf_score,
                   LEAST(1, (SELECT count(DISTINCT r.member_id)
                             FROM interaction r
                             WHERE r.product_id = c.product_id AND r.active = true) / 5.0) AS confidence
            FROM candidate c
            CROSS JOIN history h
            JOIN product_cf_similarity s ON s.product_id = c.product_id AND s.other_product_id = h.product_id
            GROUP BY c.id, c.product_id
        ),
        scored AS (
            SELECT c.*, t.taste_score,
                   COALESCE(cf.cf_score, 0) AS cf_score,
                   COALESCE(cf.confidence, 0) AS confidence,
                   COALESCE(ln(1 + c.bidder_count) / NULLIF(max(ln(1 + c.bidder_count)) OVER (), 0), 0) AS popularity,
                   GREATEST(0, 1 - EXTRACT(EPOCH FROM (c.end_at - now())) / 86400) AS urgency
            FROM candidate c
            JOIN taste t ON t.id = c.id
            LEFT JOIN collaboration cf ON cf.id = c.id
        )
        SELECT id AS auctionId, product_id AS productId, category AS category,
               current_price AS currentPrice, end_at AS endAt
        FROM scored
        ORDER BY (0.8 - 0.2 * confidence) * taste_score + 0.2 * confidence * cf_score
                 + 0.1 * popularity + 0.1 * urgency DESC, end_at, id
        LIMIT 10
        """, nativeQuery = true)
    List<AuctionRow> findPersonalAuctions(Long memberId, String model, String productIds, String weights);
}
