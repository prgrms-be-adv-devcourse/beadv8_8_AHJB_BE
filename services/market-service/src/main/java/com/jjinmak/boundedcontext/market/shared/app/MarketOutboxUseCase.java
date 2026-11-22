package com.jjinmak.boundedcontext.market.shared.app;

import com.jjinmak.boundedcontext.market.shared.domain.MarketOutbox;
import com.jjinmak.boundedcontext.market.shared.out.MarketOutboxRepository;
import com.jjinmak.boundedcontext.market.shared.out.MarketOutboxSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MarketOutboxUseCase {

    private final MarketOutboxRepository marketOutboxRepository;
    private final MarketOutboxSender marketOutboxSender;
    private final Timer publishLagTimer;

    public MarketOutboxUseCase(MarketOutboxRepository marketOutboxRepository,
                                MarketOutboxSender marketOutboxSender,
                                MeterRegistry meterRegistry) {
        this.marketOutboxRepository = marketOutboxRepository;
        this.marketOutboxSender = marketOutboxSender;
        // 저장 → 발행 완료까지 걸린 시간. 아웃박스가 하류(알림·주문)에 더하는 지연
        this.publishLagTimer = Timer.builder("market.outbox.publish.lag")
                .description("아웃박스 저장부터 발행 완료까지")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public List<MarketOutbox> findUnpublished(){
        return marketOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    // 묶음을 보내고, 앞에서부터 연속으로 성공한 행만 돌려준다
    public List<MarketOutbox> send(List<MarketOutbox> outboxes){
        int sent = marketOutboxSender.sendAll(outboxes);
        return outboxes.subList(0, sent);
    }

    public void markPublished(List<MarketOutbox> published){
        if (published.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        marketOutboxRepository.markPublished(published.stream().map(MarketOutbox::getId).toList(), now);
        published.forEach(outbox -> publishLagTimer.record(Duration.between(outbox.getCreatedAt(), now)));
    }
}
