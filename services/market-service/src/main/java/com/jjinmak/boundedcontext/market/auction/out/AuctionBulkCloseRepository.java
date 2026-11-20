package com.jjinmak.boundedcontext.market.auction.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 입찰 없는 경매 일괄 유찰 (#516).
 *
 * <p>종료 스케줄러는 대상을 한 건씩 트랜잭션으로 처리해 같은 분 1,000건이면 마지막 건이 약 2.9초 늦었다.
 * 그중 절반은 입찰이 없어 "유찰로 바꾸기"만 하면 되므로 UPDATE 한 번으로 끝낸다. 낙찰은 기존처럼 건별 처리.
 *
 * <ul>
 *   <li>조건을 WHERE에 모두 넣어 원자적으로 처리한다: 진행 중, 종료 시각 지남, 입찰 없음(영국식 bid_count = 0, 하향식은 구매 시 즉시 낙찰이라 진행 중이면 항상 입찰 없음)</li>
 *   <li>FOR UPDATE SKIP LOCKED: 같은 순간 입찰·즉시낙찰·하향식 구매가 그 행을 고치고 있으면 건너뛰고, 다음 주기의 건별 처리에 맡긴다</li>
 *   <li>version + 1: 일괄 유찰 전에 경매를 읽은 입찰 트랜잭션은 낙관적 락 충돌 → 재시도에서 다시 읽고 "진행 중 아님"으로 끝난다</li>
 *   <li>RETURNING: 실제로 유찰된 행만 돌려받아 그 경매에만 유찰 이벤트를 발행한다</li>
 *   <li>updated_at은 auditing을 거치지 않으므로 직접 넣는다(종료 지연 측정 = updated_at − end_at)</li>
 * </ul>
 *
 * <p>UPDATE ... RETURNING과 FOR UPDATE SKIP LOCKED는 PostgreSQL 문법이다. 테스트용 H2에서는 빈 결과를 돌려주고
 * 기존 건별 처리가 모든 대상을 처리한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuctionBulkCloseRepository {

    private static final String FAIL_NO_BID = """
            UPDATE auction
               SET status = 'FAILED', failure_reason = 'NO_BID', version = version + 1, updated_at = :updatedAt
             WHERE id IN (
                   SELECT id FROM auction
                    WHERE status = 'IN_PROGRESS'
                      AND end_at <= :now
                      AND (auction_type = 'DUTCH' OR bid_count = 0)
                    ORDER BY end_at
                    LIMIT :limit
                      FOR UPDATE SKIP LOCKED)
            RETURNING id, product_id, seller_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private volatile Boolean postgres;

    public List<FailedAuction> failNoBidAuctions(LocalDateTime now, LocalDateTime updatedAt, int limit) {
        if (!isPostgres()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("updatedAt", updatedAt)
                .addValue("limit", limit);
        return jdbc.query(FAIL_NO_BID, params, (rs, i) ->
                new FailedAuction(rs.getLong("id"), rs.getLong("product_id"), rs.getLong("seller_id")));
    }

    private boolean isPostgres() {
        Boolean cached = postgres;
        if (cached == null) {
            cached = Objects.requireNonNullElse(jdbc.getJdbcTemplate().execute((Connection con) ->
                    "PostgreSQL".equalsIgnoreCase(con.getMetaData().getDatabaseProductName())), false);
            if (!cached) {
                log.info("PostgreSQL이 아니라 입찰 없는 경매 일괄 유찰을 끄고 건별 처리만 한다");
            }
            postgres = cached;
        }
        return cached;
    }

    public record FailedAuction(Long auctionId, Long productId, Long sellerId) {
    }
}
