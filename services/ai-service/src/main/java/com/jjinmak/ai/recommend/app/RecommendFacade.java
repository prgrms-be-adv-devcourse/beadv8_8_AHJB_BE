package com.jjinmak.ai.recommend.app;

import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import com.jjinmak.ai.recommend.app.RecommendEmbeddingUseCase;
import com.jjinmak.ai.recommend.app.RecommendInteractionUseCase;
import com.jjinmak.ai.recommend.app.RecommendSearchUseCase;
import com.jjinmak.ai.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.ai.recommend.app.dto.SearchRequestDto;
import com.jjinmak.ai.recommend.app.dto.SearchResponseDto;
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
    private final RecommendSearchUseCase searchUseCase;

    @Transactional
    public void syncMember(MemberDto memberDto){
        recommendSyncUseCase.syncMember(memberDto);
    }

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

    public SearchResponseDto searchAuctions(Long memberId, SearchRequestDto request){
        return searchUseCase.search(memberId, request);
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
