package com.jjinmak.auction.in;

import com.jjinmak.auction.app.AuctionFacade;
import com.jjinmak.auction.dto.AuctionCreateDto;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import static com.jjinmak.contracts.order.event.OrderEventTopics.*;
import static com.jjinmak.contracts.product.event.ProductEventTopics.*;


@Component
@RequiredArgsConstructor
public class AuctionEventListener {
    private final AuctionFacade auctionFacade;

    @KafkaListener(topics = PRODUCT_REGISTERED, groupId = "auction")
    public void handleProductRegistered(ProductRegisteredEvent event) {
        auctionFacade.createAuction(toCreateDto(event, LocalDateTime.now()));
    }
    // 회원 생성 시 경매 회원을 복제한다. (입찰자 확인, 입찰 내역 닉네임)
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleMemberCreated(MemberCreatedEvent event) {
        auctionFacade.syncMember(event.member());
    }

    //낙찰 이후 거래 결과
    @KafkaListener(topics = ORDER_PAYMENT_SUCCEED, groupId = "auction")
    public void handleOrderPaymentSucceed(OrderPaymentSucceedEvent event) {
        auctionFacade.applyPaymentSucceeded(event.auctionId());
    }

    @KafkaListener(topics = CART_PAYMENT_DEFAULT, groupId ="auction")
    public void handleCartPaymentDefault(CartPaymentDefaultEvent event) {
        auctionFacade.applyPaymentDefaulted(event.auctionId());
    }

    @KafkaListener(topics = ORDER_CONFIRM_PURCHASE, groupId = "auction")
    public void handleOrderConfirmPurchase(OrderConfirmPurchaseEvent event) {
        auctionFacade.applyPurchaseConfirmed(event.auctionId());
    }

    @KafkaListener(topics = ORDER_REFUND_ACCEPTED, groupId = "auction")
    public void handleOrderRefundAccepted(OrderRefundAcceptedEvent event) {
        auctionFacade.applyRefundAccepted(event.auctionId());
    }

    private LocalDateTime resolveLegacyEndAt(ProductRegisteredEvent event, LocalDateTime startAt) {
        if (event.customEndAt() != null) {
            return event.customEndAt();
        }
        return startAt.plusDays(event.duration());
    }
    private AuctionCreateDto toCreateDto(ProductRegisteredEvent event, LocalDateTime now) {
        LocalDateTime startAt = event.scheduledAt() != null ? event.scheduledAt() : now;
        ProductRegisteredEvent.EnglishTerms english = event.english();
        return new AuctionCreateDto(
                event.productId(),
                event.sellerId(),
                event.startPrice(),
                english != null ? english.instantWinPrice() : event.instantWinPrice(),
                event.shippingFee(),
                startAt,
                english != null ? english.endAt() : resolveLegacyEndAt(event, startAt),
                event.category()
        );
    }

}
