package com.jjinmak.back.payment.app.facade;

import com.jjinmak.back.payment.app.usecase.PaymentOutboxUseCase;
import com.jjinmak.back.payment.domain.PaymentOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 아웃박스 파사드.
 *
 * Kafka 전송 중에 DB 트랜잭션(커넥션)을 잡고 있지 않도록 조회 · 전송 · 발행 표시를 나눈다.
 * 전송(send)에는 트랜잭션이 없고, 발행 표시는 건마다 별도 트랜잭션이다.
 * 전송은 됐는데 발행 표시 전에 죽으면 다음 주기에 같은 이벤트를 다시 보낸다 (수신 측이 멱등이어야 한다).
 */
@Service
@RequiredArgsConstructor
public class PaymentOutboxFacade {

    private final PaymentOutboxUseCase paymentOutboxUseCase;

    @Transactional(readOnly = true)
    public List<PaymentOutbox> findUnpublished() {
        return paymentOutboxUseCase.findUnpublished();
    }

    public void send(PaymentOutbox outbox) {
        paymentOutboxUseCase.send(outbox);
    }

    @Transactional
    public void markPublished(Long outboxId) {
        paymentOutboxUseCase.markPublished(outboxId);
    }
}
