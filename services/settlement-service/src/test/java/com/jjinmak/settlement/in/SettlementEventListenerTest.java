package com.jjinmak.settlement.in;

import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import com.jjinmak.settlement.app.SettlementFacade;
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
class SettlementEventListenerTest {

    @Mock
    SettlementFacade settlementFacade;

    @InjectMocks
    SettlementEventListener settlementEventListener;

    static Stream<Arguments> subscriptions() {
        return Stream.of(
                arguments(ProductRegisteredEvent.class, "product.registered"),
                arguments(OrderConfirmPurchaseEvent.class, "order.confirm-purchase"),
                arguments(WalletReleasedEvent.class, "payment.payment-released")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("subscriptions")
    @DisplayName("이벤트마다 그 이벤트의 토픽 하나만 settlement 그룹으로 구독한다")
    void handle_subscribesTopicWithSettlementGroup(Class<?> eventType, String topic) throws NoSuchMethodException {
        KafkaListener listener = SettlementEventListener.class.getMethod("handle", eventType).getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly(topic);
        assertThat(listener.groupId()).isEqualTo("settlement");
    }

    @Test
    @DisplayName("상품 등록 이벤트를 받으면 그 이벤트로 상품명 복사본을 저장한다")
    void productRegistered_savesProduct() {
        ProductRegisteredEvent event = new ProductRegisteredEvent(
                100L, 20L, 3_000L, 10_000L, null, 3, null, "게임보이 컬러", "CONSOLE", null);

        settlementEventListener.handle(event);

        verify(settlementFacade).saveProduct(event);
    }

    @Test
    @DisplayName("구매확정 이벤트를 받으면 그 이벤트로 정산서를 만든다")
    void confirmPurchase_createsSettlement() {
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                1L, 10L, 20L, 30L, 100L, 50_000L, 3_000L, LocalDateTime.of(2026, 11, 10, 12, 0));

        settlementEventListener.handle(event);

        verify(settlementFacade).createSettlement(event);
    }

    @Test
    @DisplayName("지갑 지급 완료 이벤트를 받으면 그 이벤트로 정산서를 완료한다")
    void walletReleased_completesSettlement() {
        WalletReleasedEvent event = new WalletReleasedEvent(
                1L, 20L, 53_000L, 5_000L, 48_000L, LocalDateTime.of(2026, 12, 15, 3, 0));

        settlementEventListener.handle(event);

        verify(settlementFacade).completeSettlement(event);
    }
}