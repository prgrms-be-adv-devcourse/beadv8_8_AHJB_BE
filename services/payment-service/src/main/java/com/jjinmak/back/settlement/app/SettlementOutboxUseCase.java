package com.jjinmak.back.settlement.app;

import com.jjinmak.back.settlement.domain.SettlementOutbox;
import com.jjinmak.back.settlement.out.SettlementOutboxRepository;
import com.jjinmak.back.settlement.out.SettlementOutboxSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementOutboxUseCase {

    private final SettlementOutboxRepository settlementOutboxRepository;
    private final SettlementOutboxSender settlementOutboxSender;

    /** 아직 보내지 않은 행을 오래된 순으로 최대 100건 */
    public List<SettlementOutbox> findUnpublished() {
        return settlementOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    /** Kafka 로 전송. 실패하면 예외 (트랜잭션 밖에서 호출) */
    public void send(SettlementOutbox outbox) {
        settlementOutboxSender.send(outbox);
    }

    /** 전송 성공한 행에 발행 시각 기록 */
    public void markPublished(Long outboxId) {
        settlementOutboxRepository.findById(outboxId)
                .ifPresent(outbox -> outbox.published(LocalDateTime.now()));
    }
}