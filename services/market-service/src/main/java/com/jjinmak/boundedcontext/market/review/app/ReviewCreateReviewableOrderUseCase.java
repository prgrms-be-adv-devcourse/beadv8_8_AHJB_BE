package com.jjinmak.boundedcontext.market.review.app;

import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import com.jjinmak.boundedcontext.market.shared.out.MarketMemberRepository;
import com.jjinmak.boundedcontext.market.review.domain.ReviewableOrder;
import com.jjinmak.boundedcontext.market.review.out.ReviewableOrderRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.product.dto.ProductSummaryDto;
import com.jjinmak.contracts.product.port.ProductReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewCreateReviewableOrderUseCase {

    private final ReviewableOrderRepository reviewableOrderRepository;
    private final MarketMemberRepository marketMemberRepository;
    private final ProductReadPort productReadPort;

    // 구매확정 이벤트를 받아 후기 작성 가능 주문을 만든다. 같은 주문이 다시 들어오면 무시한다.
    public void createReviewableOrder(OrderConfirmPurchaseEvent event){
        if (reviewableOrderRepository.existsByOrderId(event.orderId())){
            return;
        }

        MarketMember buyer = getMember(event.winnerId());
        MarketMember seller = getMember(event.sellerId());

        ProductSummaryDto product = productReadPort.getProductSummary(event.productId());

        reviewableOrderRepository.save(new ReviewableOrder(
                event.orderId(), buyer, seller,
                product.productId(), product.name(),
                event.winningPrice(), event.confirmedAt()
        ));
    }

    private MarketMember getMember(Long memberId){
        return marketMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));
    }
}
