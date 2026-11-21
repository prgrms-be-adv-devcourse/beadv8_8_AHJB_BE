package com.jjinmak.boundedcontext.market.auction.concurrency.support;

import org.springframework.jdbc.core.JdbcTemplate;

// 커밋되어 outbox에 저장된 결과 이벤트 수 (#548: 낙찰·유찰 이벤트는 Kafka 대신 outbox에 커밋과 함께 저장된다).
// 롤백된 트랜잭션의 이벤트는 남지 않으므로 "결과 이벤트 정확히 1번"을 그대로 확인할 수 있다.
public final class OutboxEventCounter {

    private OutboxEventCounter() {
    }

    public static long count(JdbcTemplate jdbcTemplate, String topic, Long auctionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_outbox WHERE topic = ? AND message_key = ?",
                Long.class, topic, String.valueOf(auctionId));
        return count == null ? 0 : count;
    }
}
