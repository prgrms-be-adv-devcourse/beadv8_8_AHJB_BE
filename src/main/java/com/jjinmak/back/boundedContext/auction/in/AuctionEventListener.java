package com.jjinmak.back.boundedContext.auction.in;

import com.jjinmak.back.boundedContext.auction.app.AuctionFacade;
import com.jjinmak.back.boundedContext.auction.dto.AuctionCreateDto;
import com.jjinmak.back.shared.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.back.shared.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.back.shared.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;
import static org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT;

@Component
@RequiredArgsConstructor
public class AuctionEventListener {

    private final AuctionFacade auctionFacade;

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void handleProductRegistered(ProductRegisteredEvent event) {
        AuctionCreateDto dto = new AuctionCreateDto(
                event.productId(),
                event.sellerId(),
                event.startPrice(),
                event.instantWinPrice(),
                event.shippingFee(),
                resolveEndAt(event, LocalDateTime.now())
        );
        auctionFacade.createAuction(dto);
    }

    // 회원 생성 시 경매 회원을 복제한다. (입찰자 확인, 입찰 내역 닉네임)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleMemberCreated(MemberCreatedEvent event) {
        auctionFacade.syncMember(event.member());
    }

    //낙찰 이후 거래 결과
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleOrderPaymentSucceed(OrderPaymentSucceedEvent event) {auctionFacade.applyPaymentSucceeded(event.auctionId());}

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleCartPaymentDefault(CartPaymentDefaultEvent event) {auctionFacade.applyPaymentDefaulted(event.auctionId());}

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleOrderConfirmPurchase(OrderConfirmPurchaseEvent event) {auctionFacade.applyPurchaseConfirmed(event.auctionId());}

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleOrderRefundAccepted(OrderRefundAcceptedEvent event) {auctionFacade.applyRefundAccepted(event.auctionId());}

    // 종료 시각 직접 지정 or 기간선택(1,3,7)
    private LocalDateTime resolveEndAt(ProductRegisteredEvent event, LocalDateTime now) {
        if (event.customEndAt() != null) {
            return event.customEndAt();
        }
        return now.plusDays(event.duration());
    }


}
