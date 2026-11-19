package com.jjinmak.back.payment.out.outbox;

import com.jjinmak.back.payment.domain.PaymentOutbox;
import com.jjinmak.back.payment.out.repository.PaymentOutboxRepository;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxWriterTest {

    @Mock
    PaymentOutboxRepository paymentOutboxRepository;

    ObjectMapper objectMapper = JsonMapper.builder().build();

    PaymentOutboxWriter paymentOutboxWriter;

    LocalDateTime at = LocalDateTime.of(2026, 11, 19, 12, 0);

    @BeforeEach
    void setUp() {
        paymentOutboxWriter = new PaymentOutboxWriter(paymentOutboxRepository, objectMapper);
    }

    @Test
    @DisplayName("결제 완료 이벤트는 payment.payment-paid 토픽과 주문 그룹 ID 키로 아웃박스에 저장한다")
    void paymentPaid_savesWithOrderGroupIdKey() {
        PaymentPaidEvent event = PaymentPaidEvent.of(500L, 7L, 1L, 50_000L, at);

        paymentOutboxWriter.write(event);

        assertSaved("payment.payment-paid", "500", event);
    }

    @Test
    @DisplayName("환불 완료 이벤트는 payment.payment-refund 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void walletRefunded_savesWithOrderIdKey() {
        WalletRefundedEvent event = new WalletRefundedEvent(9L, 1_001L, 7L, 33_000L, at);

        paymentOutboxWriter.write(event);

        assertSaved("payment.payment-refund", "1001", event);
    }

    @Test
    @DisplayName("정산 지급 완료 이벤트는 payment.payment-released 토픽과 주문 ID 키로 아웃박스에 저장한다")
    void walletReleased_savesWithOrderIdKey() {
        WalletReleasedEvent event = new WalletReleasedEvent(1_002L, 2L, 17_000L, 1_700L, 15_300L, at);

        paymentOutboxWriter.write(event);

        assertSaved("payment.payment-released", "1002", event);
    }

    private void assertSaved(String topic, String key, Object event) {
        ArgumentCaptor<PaymentOutbox> captor = ArgumentCaptor.forClass(PaymentOutbox.class);
        verify(paymentOutboxRepository).save(captor.capture());
        PaymentOutbox saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo(topic);
        assertThat(saved.getMessageKey()).isEqualTo(key);
        assertThat(objectMapper.readValue(saved.getPayload(), event.getClass())).isEqualTo(event);
        assertThat(saved.getPublishedAt()).isNull();
    }
}
