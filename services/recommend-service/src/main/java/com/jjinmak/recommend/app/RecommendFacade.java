package com.jjinmak.recommend.app;

import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendFacade {

    private final RecommendSyncUseCase recommendSyncUseCase;
    private final RecommendInteractionUseCase interactionUseCase;
    private final RecommendEmbeddingUseCase embeddingUseCase;
    private final RecommendAuctionUseCase recommendAuctionUseCase;
    private final RecommendCfSimilarityUseCase cfSimilarityUseCase;

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
        interactionUseCase.winAuction(event);
    }

    @Transactional
    public void failAuction(AuctionFailedEvent event){
        recommendSyncUseCase.failAuction(event);
    }

    @Transactional
    public void bidAuction(AuctionBidSucceedEvent event){
        interactionUseCase.bidAuction(event);
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingEmbedding(){
        return embeddingUseCase.findPendingEmbedding();
    }

    public void requestEmbedding(Long productId){
        embeddingUseCase.requestEmbedding(productId);
    }

    @Transactional(readOnly = true)
    public List<AuctionResponseDto> readRecommendedAuctionList(Long auctionId){
        return recommendAuctionUseCase.readRecommendedAuctionList(auctionId);
    }

    @Transactional(readOnly = true)
    public List<AuctionResponseDto> readPopularAuctionList(){
        return recommendAuctionUseCase.readPopularAuctionList();
    }

    @Transactional(readOnly = true)
    public List<AuctionResponseDto> readPersonalRecommendedAuctionList(Long memberId){
        return recommendAuctionUseCase.readPersonalRecommendedAuctionList(memberId);
    }

    @Transactional
    public void changeWish(ProductWishChangedEvent event){
        interactionUseCase.changeWish(event);
    }

    @Transactional
    public void refreshCfSimilarity(){
        cfSimilarityUseCase.refreshCfSimilarity();
    }
}
