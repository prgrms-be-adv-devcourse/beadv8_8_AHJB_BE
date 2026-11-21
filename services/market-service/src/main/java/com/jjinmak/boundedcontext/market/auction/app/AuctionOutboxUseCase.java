package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import com.jjinmak.boundedcontext.market.auction.out.AuctionOutboxRepository;
import com.jjinmak.boundedcontext.market.auction.out.AuctionOutboxSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuctionOutboxUseCase {

    private final AuctionOutboxRepository auctionOutboxRepository;
    private final AuctionOutboxSender auctionOutboxSender;
    private final Timer publishLagTimer;

    public AuctionOutboxUseCase(AuctionOutboxRepository auctionOutboxRepository,
                                AuctionOutboxSender auctionOutboxSender,
                                MeterRegistry meterRegistry) {
        this.auctionOutboxRepository = auctionOutboxRepository;
        this.auctionOutboxSender = auctionOutboxSender;
        // 저장 → 발행 완료까지 걸린 시간. 아웃박스가 하류(알림·주문)에 더하는 지연
        this.publishLagTimer = Timer.builder("auction.outbox.publish.lag")
                .description("아웃박스 저장부터 발행 완료까지")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public List<AuctionOutbox> findUnpublished(){
        return auctionOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    // 묶음을 보내고, 앞에서부터 연속으로 성공한 행만 돌려준다
    public List<AuctionOutbox> send(List<AuctionOutbox> outboxes){
        int sent = auctionOutboxSender.sendAll(outboxes);
        return outboxes.subList(0, sent);
    }

    public void markPublished(List<AuctionOutbox> published){
        if (published.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        auctionOutboxRepository.markPublished(published.stream().map(AuctionOutbox::getId).toList(), now);
        published.forEach(outbox -> publishLagTimer.record(Duration.between(outbox.getCreatedAt(), now)));
    }
}
