package com.jjinmak.recommend.app;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendFacade {

    private final RecommendSyncUseCase recommendSyncUseCase;

    @Transactional
    public void syncProduct(ProductChangedEvent event){
        recommendSyncUseCase.syncProduct(event);
    }

    @Transactional
    public void startAuction(AuctionStartedEvent event){
        recommendSyncUseCase.startAuction(event);
    }

    @Transactional
    public void winAuction(AuctionWonEvent event){
        recommendSyncUseCase.winAuction(event);
    }

    @Transactional
    public void failAuction(AuctionFailedEvent event){
        recommendSyncUseCase.failAuction(event);
    }
}
