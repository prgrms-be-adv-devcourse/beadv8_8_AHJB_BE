package com.jjinmak.order.in;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.contracts.refund.dto.RefundDto;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import com.jjinmak.order.app.OrderFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.contracts.refund.domain.RefundStatus.ACCEPTED;
import static com.jjinmak.contracts.refund.domain.RefundStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderKafkaListenerTest {

    @Mock
    OrderFacade orderFacade;

    @InjectMocks
    OrderKafkaListener orderKafkaListener;

    static Stream<Arguments> subscriptions() {
        return Stream.of(
                arguments(AuctionWonEvent.class, "auction.won"),
                arguments(MemberCreatedEvent.class, "member.created"),
                arguments(DeliveryCreatedEvent.class, "delivery.created"),
                arguments(DeliveryStatusUpdatedEvent.class, "delivery.status-updated"),
                arguments(PaymentPaidEvent.class, "payment.payment-paid"),
                arguments(RefundAcceptedEvent.class, "refund.accepted"),
                arguments(RefundRejectedEvent.class, "refund.rejected")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("subscriptions")
    @DisplayName("이벤트마다 그 이벤트의 토픽 하나만 order 그룹으로 구독한다")
    void handle_subscribesTopicWithOrderGroup(Class<?> eventType, String topic) throws NoSuchMethodException {
        KafkaListener listener = OrderKafkaListener.class.getMethod("handle", eventType).getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly(topic);
        assertThat(listener.groupId()).isEqualTo("order");
    }

    @Test
    @DisplayName("회원 생성 이벤트를 받으면 그 회원 정보로 동기화한다")
    void memberCreated_syncsMember() {
        MemberDto member = new MemberDto(1L, UUID.randomUUID(), "낙찰자");

        orderKafkaListener.handle(new MemberCreatedEvent(member));

        verify(orderFacade).syncMember(member);
    }

    @Test
    @DisplayName("낙찰 이벤트를 받으면 그 이벤트로 장바구니 항목을 만든다")
    void auctionWon_createsCartItem() {
        AuctionWonEvent event = new AuctionWonEvent(10L, 20L, 1L, 2L, 30_000L, 3_000L, LocalDateTime.of(2026, 10, 15, 12, 0));

        orderKafkaListener.handle(event);

        verify(orderFacade).createCartItem(event);
    }

    @Test
    @DisplayName("배송 생성 이벤트를 받으면 그 이벤트로 배송을 시작한다")
    void deliveryCreated_startsDelivery() {
        DeliveryCreatedEvent event = new DeliveryCreatedEvent(100L);

        orderKafkaListener.handle(event);

        verify(orderFacade).startDelivery(event);
    }

    @Test
    @DisplayName("배송 상태 변경 이벤트를 받으면 그 이벤트로 배송 상태를 갱신한다")
    void deliveryStatusUpdated_updatesDeliveryStatus() {
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(100L, 1L, 2L, IN_TRANSIT, DELIVERED, LocalDateTime.of(2026, 10, 15, 12, 0));

        orderKafkaListener.handle(event);

        verify(orderFacade).updateDeliveryStatus(event);
    }

    @Test
    @DisplayName("결제 완료 이벤트를 받으면 그 이벤트로 주문 결제를 완료한다")
    void paymentPaid_completesOrderPayment() {
        PaymentPaidEvent event = PaymentPaidEvent.of(50L, 7L, 1L, 33_000L, LocalDateTime.of(2026, 10, 19, 12, 0));

        orderKafkaListener.handle(event);

        verify(orderFacade).paymentPaid(event);
    }

    @Test
    @DisplayName("환불 승인 이벤트를 받으면 그 환불 정보로 환불을 승인하고, 환불을 거절하지 않는다")
    void refundAccepted_acceptsRefund() {
        RefundDto refund = new RefundDto(7L, 100L, 1L, ACCEPTED, 33_000L,
                LocalDateTime.of(2026, 10, 15, 12, 0), LocalDateTime.of(2026, 10, 16, 12, 0));

        orderKafkaListener.handle(RefundAcceptedEvent.of(refund));

        verify(orderFacade).acceptRefund(refund);
        verify(orderFacade, never()).rejectRefund(any());
    }

    @Test
    @DisplayName("환불 거절 이벤트를 받으면 그 환불 정보로 환불을 거절하고, 환불을 승인하지 않는다")
    void refundRejected_rejectsRefund() {
        RefundDto refund = new RefundDto(7L, 100L, 1L, REJECTED, 33_000L,
                LocalDateTime.of(2026, 10, 15, 12, 0), LocalDateTime.of(2026, 10, 16, 12, 0));

        orderKafkaListener.handle(RefundRejectedEvent.of(refund));

        verify(orderFacade).rejectRefund(refund);
        verify(orderFacade, never()).acceptRefund(any());
    }
}
