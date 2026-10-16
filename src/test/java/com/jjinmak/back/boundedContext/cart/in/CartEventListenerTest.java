package com.jjinmak.back.boundedContext.cart.in;

import com.jjinmak.back.boundedContext.cart.app.CartFacade;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartEventListenerTest {

    @Mock
    CartFacade cartFacade;

    @InjectMocks
    CartEventListener cartEventListener;

    @Test
    @DisplayName("회원 생성 이벤트를 받으면 그 회원 정보로 동기화한다")
    void memberCreated_syncsMember() {
        MemberDto member = new MemberDto(1L, UUID.randomUUID(), "낙찰자");

        cartEventListener.handle(new MemberCreatedEvent(member));

        verify(cartFacade).syncMember(member);
    }

    @Test
    @DisplayName("결제 성공 이벤트를 받으면 상품 id가 아니라 경매 id로 장바구니 항목을 지운다")
    void orderPaymentSucceed_usesAuctionId() {
        OrderPaymentSucceedEvent event = new OrderPaymentSucceedEvent(5L, 10L, 20L, LocalDateTime.of(2026, 10, 15, 12, 0));

        cartEventListener.handle(event);

        verify(cartFacade).paymentSucceed(10L);
        verify(cartFacade, never()).paymentSucceed(20L);
    }

    @Test
    @DisplayName("낙찰 이벤트를 받으면 그 이벤트로 장바구니 항목을 만든다")
    void auctionWon_createsCartItem() {
        AuctionWonEvent event = new AuctionWonEvent(10L, 20L, 1L, 2L, 30_000L, 3_000L, LocalDateTime.of(2026, 10, 15, 12, 0));

        cartEventListener.handle(event);

        verify(cartFacade).createCartItem(event);
    }
}
