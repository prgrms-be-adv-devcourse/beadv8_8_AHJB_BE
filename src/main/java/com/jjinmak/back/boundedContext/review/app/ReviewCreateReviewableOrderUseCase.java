package com.jjinmak.back.boundedContext.review.app;

import com.jjinmak.back.boundedContext.review.domain.ReviewMember;
import com.jjinmak.back.boundedContext.review.domain.ReviewableOrder;
import com.jjinmak.back.boundedContext.review.out.ReviewMemberRepository;
import com.jjinmak.back.boundedContext.review.out.ReviewableOrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.product.dto.ProductSummaryDto;
import com.jjinmak.back.shared.product.port.ProductReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewCreateReviewableOrderUseCase {

    private final ReviewableOrderRepository reviewableOrderRepository;
    private final ReviewMemberRepository reviewMemberRepository;
    private final ProductReadPort productReadPort;

    // 구매확정 이벤트를 받아 후기 작성 가능 주문을 만든다. 같은 주문이 다시 들어오면 무시한다.
    public void createReviewableOrder(OrderConfirmPurchaseEvent event){
        if (reviewableOrderRepository.existsByOrderId(event.orderId())){
            return;
        }

        ReviewMember buyer = getMember(event.winnerId());
        ReviewMember seller = getMember(event.sellerId());

        ProductSummaryDto product = productReadPort.getProductSummary(event.productId());

        reviewableOrderRepository.save(new ReviewableOrder(
                event.orderId(), buyer, seller,
                product.productId(), product.name(),
                event.winningPrice(), event.confirmedAt()
        ));
    }

    private ReviewMember getMember(Long memberId){
        return reviewMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));
    }
}
