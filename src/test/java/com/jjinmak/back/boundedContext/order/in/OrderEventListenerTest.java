package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.app.OrderFacade;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import com.jjinmak.back.shared.refund.event.RefundRejectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.back.shared.refund.domain.RefundStatus.ACCEPTED;
import static com.jjinmak.back.shared.refund.domain.RefundStatus.REJECTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    OrderFacade orderFacade;

    @InjectMocks
    OrderEventListener orderEventListener;

    @Test
    @DisplayName("회원 생성 이벤트를 받으면 그 회원 정보로 동기화한다")
    void memberCreated_syncsMember() {
        MemberDto member = new MemberDto(1L, UUID.randomUUID(), "낙찰자");

        orderEventListener.handle(new MemberCreatedEvent(member));

        verify(orderFacade).syncMember(member);
    }

    @Test
    @DisplayName("낙찰 이벤트를 받으면 그 이벤트로 장바구니 항목을 만든다")
    void auctionWon_createsCartItem() {
        AuctionWonEvent event = new AuctionWonEvent(10L, 20L, 1L, 2L, 30_000L, 3_000L, LocalDateTime.of(2026, 10, 15, 12, 0));

        orderEventListener.handle(event);

        verify(orderFacade).createCartItem(event);
    }

    @Test
    @DisplayName("배송 생성 이벤트를 받으면 그 이벤트로 배송을 시작한다")
    void deliveryCreated_startsDelivery() {
        DeliveryCreatedEvent event = new DeliveryCreatedEvent(100L);

        orderEventListener.handle(event);

        verify(orderFacade).startDelivery(event);
    }

    @Test
    @DisplayName("배송 상태 변경 이벤트를 받으면 그 이벤트로 배송 상태를 갱신한다")
    void deliveryStatusUpdated_updatesDeliveryStatus() {
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(100L, IN_TRANSIT, DELIVERED, LocalDateTime.of(2026, 10, 15, 12, 0));

        orderEventListener.handle(event);

        verify(orderFacade).updateDeliveryStatus(event);
    }

    @Test
    @DisplayName("환불 승인 이벤트를 받으면 그 환불 정보로 환불을 승인하고, 환불을 거절하지 않는다")
    void refundAccepted_acceptsRefund() {
        RefundDto refund = new RefundDto(7L, 100L, 1L, ACCEPTED, 33_000L,
                LocalDateTime.of(2026, 10, 15, 12, 0), LocalDateTime.of(2026, 10, 16, 12, 0));

        orderEventListener.handle(new RefundAcceptedEvent(refund));

        verify(orderFacade).acceptRefund(refund);
        verify(orderFacade, never()).rejectRefund(any());
    }

    @Test
    @DisplayName("환불 거절 이벤트를 받으면 그 환불 정보로 환불을 거절하고, 환불을 승인하지 않는다")
    void refundRejected_rejectsRefund() {
        RefundDto refund = new RefundDto(7L, 100L, 1L, REJECTED, 33_000L,
                LocalDateTime.of(2026, 10, 15, 12, 0), LocalDateTime.of(2026, 10, 16, 12, 0));

        orderEventListener.handle(new RefundRejectedEvent(refund));

        verify(orderFacade).rejectRefund(refund);
        verify(orderFacade, never()).acceptRefund(any());
    }
}
