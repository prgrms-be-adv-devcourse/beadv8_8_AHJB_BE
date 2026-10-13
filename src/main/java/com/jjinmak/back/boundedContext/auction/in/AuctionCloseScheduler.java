package com.jjinmak.back.boundedContext.auction.in;

import com.jjinmak.back.boundedContext.auction.app.AuctionFacade;
import com.jjinmak.back.boundedContext.auction.domain.AuctionCloseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {
    private final AuctionFacade auctionFacade;
    // 일단 경매 1건->트랜잭션 1개처리
    // 스케줄러 1초마다 실행
    @Scheduled(fixedDelay = 1000)
    public void closeAuctions(){
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = auctionFacade.findCloseTargetIds(now,1000);
        if (ids.isEmpty()) {return;}
        int won = 0, failed = 0, skipped = 0, conflict = 0, error = 0;
        for(Long id:ids){
            try{
                AuctionCloseResult result = auctionFacade.closeAuction(id, now);
                switch (result) {
                    case WON -> won++;
                    case FAILED -> failed++;
                    case SKIPPED -> skipped++;
                }
            }
            catch(ObjectOptimisticLockingFailureException e){
                conflict++;
                log.info("경매 종료 충돌 auctionId={}", id);
            }catch(Exception e){
                error++;
                log.warn("경매 종료 실패 auctionId={}",id,e);
            }
        }
        log.info("경매 종료 처리 대상={}, 낙찰={}, 유찰={}, 건너뜀={}, 충돌={}, 실패={}",ids.size(),won,failed,skipped,conflict,error);
    }
}

