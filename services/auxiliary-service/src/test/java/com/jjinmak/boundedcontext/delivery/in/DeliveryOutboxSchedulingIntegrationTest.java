package com.jjinmak.boundedcontext.delivery.in;

import com.jjinmak.boundedcontext.delivery.out.eventPublisher.EventPublisher;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryOutboxRepository;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deliveryoutboxscheduling;MODE=PostgreSQL")
class DeliveryOutboxSchedulingIntegrationTest {

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DeliveryOutboxRepository deliveryOutboxRepository;

    @AfterEach
    void tearDown() {
        deliveryOutboxRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("커밋된 아웃박스는 직접 호출하지 않아도 스케줄러가 주기적으로 보내고 발행으로 표시한다")
    void scheduledRelay_sendsCommittedOutbox() {
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(anyString(), anyString(), any());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new DeliveryCreatedEvent(1L)));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(deliveryOutboxRepository.findAll())
                        .singleElement()
                        .satisfies(outbox -> assertThat(outbox.getPublishedAt()).isNotNull()));
        verify(kafkaTemplate, atLeastOnce()).send(eq("delivery.created"), eq("1"), any());
    }
}
