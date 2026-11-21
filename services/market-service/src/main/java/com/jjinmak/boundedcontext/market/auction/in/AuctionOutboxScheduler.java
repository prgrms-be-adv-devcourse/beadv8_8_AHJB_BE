package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// 경매 아웃박스 전송 (#548). 구조(조회 → 전송 → 발행 표시, 1초 주기, 실패 시 그 회차 중단)는 팀 공통 방식과 같다.
// 다른 점: 100건 묶음을 한꺼번에 보내고 한 번에 표시하며, 묶음이 가득 차 있으면 쉬지 않고 이어서 보낸다.
// (같은 분 종료 1,000건에서 팀 방식은 마지막 낙찰 이벤트가 약 20초 늦게 나갔다, 2차 측정)
// 브로커가 멈추면 이 작업만 기다리고 경매 종료·열기 스케줄러는 영향을 받지 않는다.
// 테스트에서는 market.outbox.relay.enabled=false로 끈다(KafkaTemplate이 mock).
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionOutboxScheduler {

    static final int BATCH_SIZE = 100;            // AuctionOutboxRepository.findTop100…
    static final int MAX_BATCHES_PER_RUN = 20;    // 한 번 실행에 최대 2,000건

    private final AuctionFacade auctionFacade;

    @Scheduled(fixedDelay = 1_000)
    public void sendOutbox(){
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            List<AuctionOutbox> batch = auctionFacade.findUnpublishedOutboxes();
            if (batch.isEmpty()) {
                return;
            }
            List<AuctionOutbox> sent = auctionFacade.sendOutboxes(batch);
            auctionFacade.markOutboxesPublished(sent);
            if (sent.size() < batch.size()) {
                return;   // 실패: 나머지는 다음 주기에 실패한 행부터 다시 (로그는 전송기에서)
            }
            if (batch.size() < BATCH_SIZE) {
                return;   // 다 보냄
            }
        }
    }
}
