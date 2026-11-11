package com.jjinmak.recommend.out;

import com.jjinmak.recommend.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    boolean existsByMemberAndAuctionAndType(RecommendMember member, RecommendAuction auction, InteractionType type);

    Optional<Interaction> findByMemberAndAuctionAndType(RecommendMember member, RecommendAuction auction, InteractionType type);

    @Query(value = """
        WITH candidate AS (
            SELECT a.id, a.product_id, p.category, a.current_price, a.end_at,
                   count(i.id) AS bidder_count
            FROM recommend_auction a
            JOIN recommend_product p ON p.id = a.product_id AND p.deleted = false
            LEFT JOIN interaction i ON i.auction_id = a.id AND i.type = 'BID'
            WHERE a.status = 'ACTIVE' AND a.end_at > now()
            GROUP BY a.id, p.id
        ),
        scored AS (
            SELECT c.*,
                   COALESCE(ln(1 + c.bidder_count) / NULLIF(max(ln(1 + c.bidder_count)) OVER (), 0), 0) AS popularity,
                   GREATEST(0, 1 - EXTRACT(EPOCH FROM (c.end_at - now())) / 86400) AS urgency
            FROM candidate c
        )
        SELECT id AS auctionId, product_id AS productId, category AS category,
               current_price AS currentPrice, end_at AS endAt
        FROM scored
        ORDER BY 0.7 * popularity + 0.3 * urgency DESC, end_at, id
        LIMIT 10
        """, nativeQuery = true)
    List<AuctionRow> findPopularAuctions();
}
