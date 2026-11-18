package com.jjinmak.boundedcontext.auction.in;

import com.jjinmak.boundedcontext.auction.app.AuctionFacade;
import com.jjinmak.common.kafka.InvalidEventException;
import com.jjinmak.contracts.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.order.event.OrderRefundAcceptedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.jjinmak.contracts.member.event.MemberEventTopics.MEMBER_CREATED;

import static com.jjinmak.contracts.order.event.OrderEventTopics.*;


// 경매 생성은 상품 이벤트가 아니라 market 흐름(MarketAuctionOpenUseCase)의 직접 호출로 한다.
@Component
@RequiredArgsConstructor
public class AuctionEventListener {
    private final AuctionFacade auctionFacade;

    // 회원 생성 시 경매 회원을 복제한다. (입찰자 확인, 입찰 내역 닉네임)
    @KafkaListener(topics = MEMBER_CREATED, groupId = "auction")
    public void handleMemberCreated(MemberCreatedEvent event) {
        MemberDto member = event.member();
        if (member == null || member.id() == null || member.nickname() == null || member.nickname().isBlank()) {
            throw new InvalidEventException("회원 생성 이벤트에 회원 id나 닉네임이 없습니다. member=" + member);
        }
        auctionFacade.syncMember(member);
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
}
