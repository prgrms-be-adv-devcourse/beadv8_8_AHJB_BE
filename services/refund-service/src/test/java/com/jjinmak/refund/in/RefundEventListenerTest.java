package com.jjinmak.refund.in;

import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.refund.app.RefundFacade;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundEventListenerTest {

    @Mock
    RefundFacade refundFacade;

    @InjectMocks
    RefundEventListener refundEventListener;

    static Stream<Arguments> subscriptions() {
        return Stream.of(
                arguments(OrderRefundRequestedEvent.class, "order.refund-requested"),
                arguments(WalletRefundedEvent.class, "payment.payment-refund")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("subscriptions")
    @DisplayName("이벤트마다 그 이벤트의 토픽 하나만 refund 그룹으로 구독한다")
    void handle_subscribesTopicWithRefundGroup(Class<?> eventType, String topic) throws NoSuchMethodException {
        KafkaListener listener = RefundEventListener.class.getMethod("handle", eventType).getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly(topic);
        assertThat(listener.groupId()).isEqualTo("refund");
    }

    @Test
    @DisplayName("환불 요청 이벤트를 받으면 그 이벤트로 환불을 만든다")
    void refundRequested_createsRefund() {
        OrderRefundRequestedEvent event = OrderRefundRequestedEvent.byWinner(1L, 10L, 20L, 100L, 50_000L, 3_000L);

        refundEventListener.handle(event);

        verify(refundFacade).createRefund(event);
    }

    @Test
    @DisplayName("지갑 환불 완료 이벤트를 받으면 그 이벤트로 환불을 완료한다")
    void walletRefunded_completesRefund() {
        WalletRefundedEvent event = new WalletRefundedEvent(1L, 1L, 7L, 53_000L, LocalDateTime.of(2026, 11, 10, 12, 0));

        refundEventListener.handle(event);

        verify(refundFacade).completeRefund(event);
    }
}