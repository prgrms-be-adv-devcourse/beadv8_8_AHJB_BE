package com.jjinmak.boundedcontext.market.auction.in;

import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

// 커밋된 상태 변경만 실시간 구독자에게 보낸다 (#557). 롤백된 입찰은 나가지 않는다.
// 전송은 비동기(전용 스레드)라 커밋한 요청 스레드는 기다리지 않는다
@Component
@RequiredArgsConstructor
public class AuctionStreamEventListener {

    private final AuctionFacade auctionFacade;

    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void on(AuctionBidSucceedEvent event) {
        auctionFacade.pushStreamBid(event);
        auctionFacade.pushStreamAuction(event.auctionId());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void on(AuctionWonEvent event) {
        auctionFacade.pushStreamAuction(event.auctionId());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void on(AuctionFailedEvent event) {
        auctionFacade.pushStreamAuction(event.auctionId());
    }
}
