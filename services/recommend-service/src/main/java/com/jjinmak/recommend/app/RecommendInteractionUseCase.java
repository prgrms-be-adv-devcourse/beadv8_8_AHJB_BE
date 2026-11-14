package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductWishChangeType;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import com.jjinmak.recommend.domain.*;
import com.jjinmak.recommend.out.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;
import static com.jjinmak.contracts.product.event.ProductWishChangeType.WISHED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendInteractionUseCase {

    private final RecommendSupport recommendSupport;
    private final InteractionRepository interactionRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final RecommendMemberRepository recommendMemberRepository;
    private final RecommendProductRepository recommendProductRepository;
    private final RecommendAuctionRepository recommendAuctionRepository;
    private final PendingInteractionRepository pendingInteractionRepository;

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

        createInteractionLog(winner, auction, product, InteractionType.WIN, event.winAt());
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

        auction.setEndAt(event.endAt());

        interaction.update(event.bidAt());

        interactionRepository.save(interaction);

        createInteractionLog(bidder, auction, product, InteractionType.BID, event.bidAt());
    }

    private void createInteractionLog(
            RecommendMember member, RecommendAuction auction, RecommendProduct product, InteractionType type, LocalDateTime occurredAt
    ){
        if (interactionLogRepository.existsByMemberAndAuctionAndTypeAndLastOccurredAt(member, auction, type, occurredAt))
            return;

        InteractionLog interactionLog = new InteractionLog(
                member, auction, product, type, occurredAt
        );

        interactionLogRepository.save(interactionLog);
    }

    public void changeWish(ProductWishChangedEvent event){
        Long memberId = event.memberId();
        Long productId = event.productId();
        Long auctionId = event.auctionId();
        ProductWishChangeType changeType = event.changeType();
        LocalDateTime occurredAt = event.occurredAt();

        if (auctionId == null){
            pendingInteractionRepository.save(new PendingInteraction(memberId, productId, changeType, occurredAt));
            return;
        }

        recommendSupport.updateWish(memberId, productId, auctionId, changeType, occurredAt);
    }
}
