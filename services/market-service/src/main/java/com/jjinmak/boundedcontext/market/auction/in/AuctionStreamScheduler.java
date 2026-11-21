package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 실시간 구독 주기 작업 (#557). 연결이 없으면 할 일이 없다 (경매 목록이 비어 바로 끝남)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market.auction.stream.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionStreamScheduler {

    private final AuctionFacade auctionFacade;

    // 시청자 수: 1초 단위로 묶어서, 바뀐 경매에만
    @Scheduled(fixedDelay = 1_000)
    public void broadcastViewers() {
        auctionFacade.broadcastStreamViewers();
    }

    // 하트비트: 프록시 읽기 타임아웃(보통 60초)보다 짧게
    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        auctionFacade.streamHeartbeat();
    }
}
