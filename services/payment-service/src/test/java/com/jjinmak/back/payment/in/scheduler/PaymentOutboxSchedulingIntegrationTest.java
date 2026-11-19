package com.jjinmak.back.payment.in.scheduler;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.payment.out.repository.PaymentOutboxRepository;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

// 다른 아웃박스 테스트는 스케줄러를 목으로 바꾸고 직접 호출한다. 여기서는 실제 스케줄링이 도는지만 본다
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:paymentoutboxscheduling;MODE=PostgreSQL")
class PaymentOutboxSchedulingIntegrationTest {

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    PaymentOutboxRepository paymentOutboxRepository;

    @AfterEach
    void tearDown() {
        paymentOutboxRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("커밋된 아웃박스는 직접 호출하지 않아도 스케줄러가 주기적으로 보내고 발행으로 표시한다")
    void scheduledRelay_sendsCommittedOutbox() {
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(anyString(), anyString(), any());

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(new WalletRefundedEvent(9L, 1_001L, 7L, 33_000L, LocalDateTime.now())));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(paymentOutboxRepository.findAll())
                        .singleElement()
                        .satisfies(outbox -> assertThat(outbox.getPublishedAt()).isNotNull()));
        verify(kafkaTemplate, atLeastOnce()).send(eq("payment.payment-refund"), eq("1001"), any());
    }
}
