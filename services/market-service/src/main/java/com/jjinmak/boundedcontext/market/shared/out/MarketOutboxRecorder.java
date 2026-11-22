package com.jjinmak.boundedcontext.market.shared.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 이벤트를 market_outbox에 저장한다 (#548).
 *
 * <p>트랜잭션 안에서 받은 이벤트를 모아 두었다가 커밋 직전(beforeCommit)에 JDBC 일괄 INSERT 한 번으로 저장한다.
 * 상태 변경과 같은 트랜잭션이라 함께 커밋·롤백된다(롤백이면 모은 이벤트는 버려진다).
 *
 * <p>팀 공통 방식은 BEFORE_COMMIT 리스너에서 엔티티를 1건씩 save한다. 경매는 같은 분 종료처럼 한 트랜잭션에서
 * 500건이 나와서, 그 방식으로는 저장에만 약 1초가 걸려 다음 처리(일괄 낙찰)가 밀렸다(3차 측정) → 모아서 한 번에 넣는다.
 *
 * <p>쓰기 트랜잭션 밖에서 발행된 이벤트는 기존 AFTER_COMMIT 발행과 같이 버린다(경고 로그).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketOutboxRecorder {

    private static final String INSERT = """
            INSERT INTO market_outbox (created_at, updated_at, topic, message_key, payload)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void record(String topic, Long key, Object event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            log.warn("쓰기 트랜잭션 밖에서 발행된 이벤트라 아웃박스에 저장하지 않음 topic={}, key={}", topic, key);
            return;
        }
        pending().add(new Row(topic, String.valueOf(key), objectMapper.writeValueAsString(event)));
    }

    @SuppressWarnings("unchecked")
    private List<Row> pending() {
        List<Row> rows = (List<Row>) TransactionSynchronizationManager.getResource(this);
        if (rows == null) {
            List<Row> created = new ArrayList<>();
            TransactionSynchronizationManager.bindResource(this, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    insertAll(created);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(MarketOutboxRecorder.this);
                }
            });
            rows = created;
        }
        return rows;
    }

    // TransactionSynchronization에도 flush()가 있어 이름이 겹치지 않게 한다
    private void insertAll(List<Row> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.batchUpdate(INSERT, rows, rows.size(), (ps, row) -> {
            ps.setTimestamp(1, now);
            ps.setTimestamp(2, now);
            ps.setString(3, row.topic());
            ps.setString(4, row.messageKey());
            ps.setString(5, row.payload());
        });
    }

    private record Row(String topic, String messageKey, String payload) {
    }
}
