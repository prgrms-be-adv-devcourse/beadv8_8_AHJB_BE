package com.jjinmak.boundedcontext.product.review.in;

import com.jjinmak.boundedcontext.product.review.app.ReviewFacade;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewEventListener {

    private final ReviewFacade reviewFacade;

    // 구매확정되면 후기를 작성할 수 있게 된다.
    @KafkaListener(topics = OrderEventTopics.ORDER_CONFIRM_PURCHASE, groupId = "review")
    public void handle(OrderConfirmPurchaseEvent event){
        reviewFacade.createReviewableOrder(event);
    }
}
