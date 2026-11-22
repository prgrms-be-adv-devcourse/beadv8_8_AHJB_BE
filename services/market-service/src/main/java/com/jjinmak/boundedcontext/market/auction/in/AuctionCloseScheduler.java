package com.jjinmak.boundedcontext.market.auction.in;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.jjinmak.boundedcontext.market.auction.in.AuctionCloseRunner.Trigger.POLL;

// 경매 종료 안전망 폴링 (#548 4차).
// 종료 시각에 맞춰 깨우는 것은 AuctionCloseTimer(타이밍 휠)가 한다. 이 폴링은 타이머가 놓친 경매
// (서버 재시작 직후, 미리 읽기 사이에 생긴 경매, 예외)를 잡는다. 기본 5초(이전에는 이 폴링이 1초마다 종료를 맡았다).
// 타이머를 끄면(market.auction.close.timer.enabled=false) 이 값을 1000으로 되돌린다.
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionCloseRunner auctionCloseRunner;

    @Scheduled(fixedDelayString = "${market.auction.close.safety-poll-ms:5000}")
    public void closeAuctions() {
        auctionCloseRunner.run(POLL);
    }
}
