package com.jjinmak.product.review.in;

import com.jjinmak.product.review.app.ReviewFacade;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewEventListener {

    private final ReviewFacade reviewFacade;

    // 회원 가입 시 회원 서비스가 보내는 이벤트로 후기 작성자·대상 정보를 복제해 둔다.
    // (같은 회원이 다시 와도 닉네임만 갱신한다)
    @KafkaListener(topics = MemberEventTopics.MEMBER_CREATED, groupId = "review")
    public void handle(MemberCreatedEvent event){
        reviewFacade.syncMember(event.member());
    }

    // 구매확정되면 후기를 작성할 수 있게 된다.
    @KafkaListener(topics = OrderEventTopics.ORDER_CONFIRM_PURCHASE, groupId = "review")
    public void handle(OrderConfirmPurchaseEvent event){
        reviewFacade.createReviewableOrder(event);
    }
}
