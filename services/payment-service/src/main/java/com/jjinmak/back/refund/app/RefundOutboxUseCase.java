package com.jjinmak.back.refund.app;

import com.jjinmak.back.refund.domain.RefundOutbox;
import com.jjinmak.back.refund.out.RefundOutboxRepository;
import com.jjinmak.back.refund.out.RefundOutboxSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundOutboxUseCase {

    private final RefundOutboxRepository refundOutboxRepository;
    private final RefundOutboxSender refundOutboxSender;

    /** 아직 보내지 않은 행을 오래된 순으로 최대 100건 (같은 환불의 이벤트 순서 유지) */
    public List<RefundOutbox> findUnpublished() {
        return refundOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    /** Kafka 로 전송. 실패하면 예외 (트랜잭션 밖에서 호출) */
    public void send(RefundOutbox outbox) {
        refundOutboxSender.send(outbox);
    }

    /** 전송 성공한 행에 발행 시각 기록. 행이 없으면(이미 정리됨) 조용히 넘어간다 */
    public void markPublished(Long outboxId) {
        refundOutboxRepository.findById(outboxId)
                .ifPresent(outbox -> outbox.published(LocalDateTime.now()));
    }
}