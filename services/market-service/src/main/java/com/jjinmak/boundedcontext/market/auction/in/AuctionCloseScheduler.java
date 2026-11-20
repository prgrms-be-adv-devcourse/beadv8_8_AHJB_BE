package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.domain.AuctionCloseResult;
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
    private static final int BATCH_SIZE = 1000;

    // 1초마다 실행
    // 1) 입찰 없는 경매(영국식 입찰 0건, 하향식)는 UPDATE 한 번으로 일괄 유찰 (#516)
    // 2) 남은 대상(낙찰)은 경매 1건 -> 트랜잭션 1개로 처리
    //    일괄 유찰이 실패해도 2)의 대상 조회에 입찰 없는 경매가 그대로 포함되어 건별로 처리된다
    @Scheduled(fixedDelay = 1000)
    public void closeAuctions(){
        LocalDateTime now = LocalDateTime.now();
        int bulkFailed = 0;
        try {
            bulkFailed = auctionFacade.failNoBidAuctions(now, BATCH_SIZE);
        } catch (Exception e) {
            log.warn("입찰 없는 경매 일괄 유찰 실패, 건별 처리로 진행", e);
        }
        List<Long> ids = auctionFacade.findCloseTargetIds(now,BATCH_SIZE);
        if (ids.isEmpty()) {
            if (bulkFailed > 0) {
                log.info("경매 종료 처리 일괄유찰={}", bulkFailed);
            }
            return;
        }
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
        log.info("경매 종료 처리 일괄유찰={}, 건별 대상={}, 낙찰={}, 유찰={}, 건너뜀={}, 충돌={}, 실패={}",
                bulkFailed,ids.size(),won,failed,skipped,conflict,error);
    }
}
