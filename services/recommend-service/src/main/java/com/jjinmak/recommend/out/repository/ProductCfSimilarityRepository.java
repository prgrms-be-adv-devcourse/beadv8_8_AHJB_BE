package com.jjinmak.recommend.out.repository;

import com.jjinmak.recommend.domain.ProductCfSimilarity;
import com.jjinmak.recommend.domain.ProductCfSimilarityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductCfSimilarityRepository extends JpaRepository<ProductCfSimilarity, ProductCfSimilarityId> {

    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryLock(long key);

    @Modifying
    @Query(value = """
        WITH reaction AS (
            SELECT i.member_id, i.product_id,
                   max(CASE i.type
                           WHEN 'WIN' THEN :winWeight
                           WHEN 'BID' THEN :bidWeight
                           WHEN 'WISH' THEN :wishWeight
                       END) AS weight
            FROM interaction i
            WHERE i.active = true
            GROUP BY i.member_id, i.product_id
        ),
        norm AS (
            SELECT product_id, sqrt(sum(weight * weight)) AS norm
            FROM reaction
            GROUP BY product_id
        ),
        candidate AS (
            SELECT DISTINCT a.product_id
            FROM recommend_auction a
            JOIN recommend_product p ON p.id = a.product_id AND p.deleted = false
            WHERE a.status = 'ACTIVE' AND a.end_at > now()
        ),
        pair AS (
            SELECT x.product_id, y.product_id AS other_product_id,
                   sum(x.weight * y.weight) AS dot, count(*) AS co_count
            FROM reaction x
            JOIN candidate c ON c.product_id = x.product_id
            JOIN reaction y ON y.member_id = x.member_id AND y.product_id <> x.product_id
            GROUP BY x.product_id, y.product_id
            HAVING count(*) >= :minCoCount
        )
        INSERT INTO product_cf_similarity (product_id, other_product_id, score, co_count, computed_at)
        SELECT pr.product_id, pr.other_product_id, pr.dot / (nx.norm * ny.norm), pr.co_count, now()
        FROM pair pr
        JOIN norm nx ON nx.product_id = pr.product_id
        JOIN norm ny ON ny.product_id = pr.other_product_id
        """, nativeQuery = true)
    int insertCfSimilarities(double winWeight, double bidWeight, double wishWeight, int minCoCount);
}
