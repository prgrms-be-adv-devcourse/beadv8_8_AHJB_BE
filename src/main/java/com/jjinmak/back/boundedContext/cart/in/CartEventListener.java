package com.jjinmak.back.boundedContext.cart.in;

import com.jjinmak.back.boundedContext.cart.app.CartFacade;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class CartEventListener {

    private final CartFacade cartFacade;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(MemberCreatedEvent event){
        MemberDto member = event.member();

        cartFacade.syncMember(member);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(OrderPaymentSucceedEvent event){
        Long productId = event.productId();

        cartFacade.paymentSucceed(productId);
    }
}
