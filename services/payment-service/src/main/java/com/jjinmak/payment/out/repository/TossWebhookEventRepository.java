package com.jjinmak.payment.out.repository;

import com.jjinmak.payment.domain.TossWebhookEvent;
import com.jjinmak.payment.domain.enums.WebhookEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TossWebhookEventRepository extends JpaRepository<TossWebhookEvent, Long> {

    /**
     * 처리 대상 조회 (스케줄러용).
     * 예: status IN (RECEIVED, FAILED) AND retryCount < 최대 횟수, 오래된 것부터
     */
    List<TossWebhookEvent> findByStatusInAndRetryCountLessThanOrderByIdAsc(
            Collection<WebhookEventStatus> statuses, int maxRetryCount, Pageable pageable);

    /** 같은 주문의 수신 내역 (운영 확인용) */
    List<TossWebhookEvent> findByOrderIdOrderByIdAsc(String orderId);
}
