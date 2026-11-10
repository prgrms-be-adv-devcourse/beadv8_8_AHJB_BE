package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.recommend.domain.Interaction;
import com.jjinmak.recommend.domain.InteractionType;
import com.jjinmak.recommend.domain.RecommendMember;
import com.jjinmak.recommend.domain.RecommendProduct;
import com.jjinmak.recommend.out.InteractionRepository;
import com.jjinmak.recommend.out.RecommendMemberRepository;
import com.jjinmak.recommend.out.RecommendProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;
import static com.jjinmak.recommend.exception.RecommendErrorCode.PRODUCT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecommendInteractionUseCase {

    private final InteractionRepository interactionRepository;
    private final RecommendMemberRepository recommendMemberRepository;
    private final RecommendProductRepository recommendProductRepository;

    public void winAuction(AuctionWonEvent event){

        Long winnerId = event.winnerId();
        Long productId = event.productId();

        RecommendMember winner = recommendMemberRepository.findById(winnerId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        RecommendProduct product = recommendProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND));

        if (interactionRepository.existsByMemberAndProductAndType(winner, product, InteractionType.WIN))
            return;

        Interaction interaction = new Interaction(
                winner, product, InteractionType.WIN, event.winAt()
        );

        interactionRepository.save(interaction);
    }
}
