package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.recommend.domain.*;
import com.jjinmak.recommend.out.InteractionRepository;
import com.jjinmak.recommend.out.RecommendAuctionRepository;
import com.jjinmak.recommend.out.RecommendMemberRepository;
import com.jjinmak.recommend.out.RecommendProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendInteractionUseCase {

    private final RecommendSupport recommendSupport;
    private final InteractionRepository interactionRepository;
    private final RecommendMemberRepository recommendMemberRepository;
    private final RecommendProductRepository recommendProductRepository;
    private final RecommendAuctionRepository recommendAuctionRepository;

    public void winAuction(AuctionWonEvent event){

        Long winnerId = event.winnerId();
        Long auctionId = event.auctionId();
        Long productId = event.productId();

        RecommendMember winner = recommendMemberRepository.findById(winnerId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        RecommendAuction auction = recommendAuctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_FOUND));

        RecommendProduct product = recommendProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND));

        if (interactionRepository.existsByMemberAndAuctionAndType(winner, auction, InteractionType.WIN))
            return;

        Interaction interaction = new Interaction(
                winner, auction, product, InteractionType.WIN, event.winAt()
        );

        interactionRepository.save(interaction);
    }

    public void bidAuction(AuctionBidSucceedEvent event){

        Long bidderId = event.bidderId();
        Long auctionId = event.auctionId();
        Long productId = event.productId();

        RecommendMember bidder = recommendSupport.findMemberById(bidderId);
        RecommendAuction auction = recommendSupport.findAuctionById(auctionId);
        RecommendProduct product = recommendSupport.findProductById(productId);

        Interaction interaction = interactionRepository.findByMemberAndAuctionAndType(bidder, auction, InteractionType.BID)
                .orElse(new Interaction(
                                bidder, auction, product, InteractionType.BID, event.bidAt()
                ));

        interaction.update(event.bidAt());

        interactionRepository.save(interaction);
    }
}
