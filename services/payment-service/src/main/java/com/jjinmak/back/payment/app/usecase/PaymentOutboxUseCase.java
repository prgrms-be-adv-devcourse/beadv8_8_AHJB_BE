package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.domain.PaymentOutbox;
import com.jjinmak.back.payment.out.kafka.PaymentOutboxSender;
import com.jjinmak.back.payment.out.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class PaymentOutboxUseCase {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentOutboxSender paymentOutboxSender;

    public List<PaymentOutbox> findUnpublished() {
        return paymentOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
    }

    public void send(PaymentOutbox outbox) {
        paymentOutboxSender.send(outbox);
    }

    public void markPublished(Long outboxId) {
        paymentOutboxRepository.findById(outboxId)
                .ifPresent(outbox -> outbox.published(LocalDateTime.now()));
    }
}
