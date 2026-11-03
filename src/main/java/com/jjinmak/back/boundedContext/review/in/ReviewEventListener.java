package com.jjinmak.back.boundedContext.review.in;

import com.jjinmak.back.boundedContext.review.app.ReviewFacade;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderEventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class ReviewEventListener {

    private final ReviewFacade reviewFacade;

    // 회원 컨텍스트가 아직 Kafka로 발행하지 않아 Spring 이벤트로 받는다.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(MemberCreatedEvent event){
        reviewFacade.syncMember(event.member());
    }

    // 구매확정되면 후기를 작성할 수 있게 된다.
    @KafkaListener(topics = OrderEventTopics.ORDER_CONFIRM_PURCHASE, groupId = "review")
    public void handle(OrderConfirmPurchaseEvent event){
        reviewFacade.createReviewableOrder(event);
    }
}
