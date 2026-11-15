package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.product.event.ProductWishChangeType;
import com.jjinmak.recommend.domain.*;
import com.jjinmak.recommend.out.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.common.exception.CommonErrorCode.*;
import static com.jjinmak.contracts.product.event.ProductWishChangeType.WISHED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.AUCTION_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendSupport {

    private final RecommendMemberRepository recommendMemberRepository;
    private final RecommendAuctionRepository recommendAuctionRepository;
    private final RecommendProductRepository recommendProductRepository;
    private final InteractionRepository interactionRepository;

    public RecommendMember findMemberById(Long id){
        return recommendMemberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public RecommendAuction findAuctionById(Long id){
        return recommendAuctionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(AUCTION_NOT_FOUND));
    }

    public RecommendProduct findProductById(Long id){
        return recommendProductRepository.findById(id)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND));
    }

    public void updateWish(
            Long memberId, Long productId, Long auctionId,
            ProductWishChangeType changeType, LocalDateTime occurredAt
    ){

        RecommendMember member = findMemberById(memberId);
        RecommendProduct product = findProductById(productId);
        RecommendAuction auction = findAuctionById(auctionId);

        Interaction interaction = interactionRepository.findByMemberAndAuctionAndType(member, auction, InteractionType.WISH)
                .orElse(new Interaction(member, auction, product, InteractionType.WISH, occurredAt));

        if (interaction.isNew(occurredAt)){
            if (changeType == WISHED){
                interaction.activate();
            } else {
                interaction.deactivate();
            }
            interaction.update(occurredAt);
        }

        interactionRepository.save(interaction);
    }
}
