package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 경매 아웃박스 전송 (#548, 팀 공통 방식).
// 저장 순서대로 보내고, 실패하면 그 회차를 멈춰 다음 회차에 그 행부터 다시 보낸다(같은 경매 이벤트 순서 유지).
// 브로커가 멈추면 이 작업만 기다리고 경매 종료·열기 스케줄러는 영향을 받지 않는다.
// 테스트에서는 market.outbox.relay.enabled=false로 끈다(KafkaTemplate이 mock).
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionOutboxScheduler {

    private final AuctionFacade auctionFacade;

    @Scheduled(fixedDelay = 1_000)
    public void sendOutbox(){
        for (AuctionOutbox outbox : auctionFacade.findUnpublishedOutboxes()){
            try {
                auctionFacade.sendOutbox(outbox);
            } catch (Exception e){
                log.error("Auction 이벤트 발행 실패, 다음 주기에 다시 보냅니다.\noutboxId: {}\ntopic: {}", outbox.getId(), outbox.getTopic(), e);
                return;
            }
            auctionFacade.markOutboxPublished(outbox.getId());
        }
    }
}
