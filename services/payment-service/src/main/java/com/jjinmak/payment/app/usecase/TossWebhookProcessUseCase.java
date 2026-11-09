package com.jjinmak.payment.app.usecase;

import com.jjinmak.payment.app.support.ChargeRecoverySupport;
import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.TossWebhookEvent;
import com.jjinmak.payment.exception.PgApiException;
import com.jjinmak.payment.out.ChargeRepository;
import com.jjinmak.payment.out.TossPaymentsClient;
import com.jjinmak.payment.out.TossWebhookEventRepository;
import com.jjinmak.payment.out.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.jjinmak.payment.domain.enums.WebhookEventStatus.FAILED;
import static com.jjinmak.payment.domain.enums.WebhookEventStatus.RECEIVED;
import static com.jjinmak.payment.out.TossPaymentsClient.NOT_FOUND_PAYMENT;

/**
 * 저장된 토스 웹훅 처리. (파사드 트랜잭션 안에서 실행, 웹훅 1건 단위)
 *
 *   웹훅 조회 → 처리 대상 거르기 → 토스 조회 → 충전 건 매칭 → 상태 비교 → PROCESSED / IGNORED / FAILED
 *
 * 웹훅 본문은 위조될 수 있으므로 상태 판단은 항상 토스 조회 결과로만 한다.
 * 충전 건 매칭도 본문의 orderId 가 아니라 토스 조회 결과의 orderId(= pgOrderId) 로 한다.
 *
 * 우리 충전 상태별 처리
 *  - REQUESTED
 *      · 충전 요청 후 APPROVAL_GRACE 가 지나지 않음 → 이번엔 건너뜀 (우리 승인 요청이 진행 중일 수 있음)
 *      · 토스 DONE / ABORTED / EXPIRED / CANCELED → ChargeRecoverySupport 로 반영 (40분 복구 스케줄러와 같은 정책)
 *      · 토스 READY / IN_PROGRESS 등 진행 중 → IGNORED (아직 결제 중일 수 있어 건드리지 않고, 복구 스케줄러가 정리)
 *  - CHARGED / USED
 *      · 토스 DONE → IGNORED (이미 반영됨)
 *      · 토스 CANCELED / PARTIAL_CANCELED → FAILED + 에러 로그 (외부 취소. 자동 반영은 별도 이슈)
 *  - CANCELED / ABORTED
 *      · 토스 DONE → FAILED + 에러 로그 (토스는 결제 완료인데 우리는 닫힘)
 *      · 그 외 → IGNORED (이미 반영됨)
 *
 * 처리 중 예외(토스 통신 실패 등)는 그대로 던진다. 트랜잭션이 롤백되므로 실패 기록은 호출하는 쪽이 recordFailure 로 따로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossWebhookProcessUseCase {

    // 충전 요청 후 이 시간이 지나야 웹훅으로 복구한다. 우리 승인 요청과 겹치지 않게 하기 위함
    public static final Duration APPROVAL_GRACE = Duration.ofMinutes(5);
    // 실패 재시도 한도. 넘으면 FAILED 로 남고 더 이상 처리하지 않는다
    public static final int MAX_RETRY = 5;

    private static final int BATCH_SIZE = 100;
    // 지금 처리하는 이벤트. 가상계좌(DEPOSIT_CALLBACK) 등은 도입 시 추가
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of("PAYMENT_STATUS_CHANGED");

    private final TossWebhookEventRepository tossWebhookEventRepository;
    private final ChargeRepository chargeRepository;
    private final TossPaymentsClient toss;
    private final ChargeRecoverySupport chargeRecoverySupport;


    // 처리 대상 id (RECEIVED / FAILED 중 재시도 한도 미만, 오래된 것부터)
    public List<Long> findProcessableIds() {
        return tossWebhookEventRepository.findByStatusInAndRetryCountLessThanOrderByIdAsc(
                        List.of(RECEIVED, FAILED), MAX_RETRY, PageRequest.of(0, BATCH_SIZE))
                .stream().map(TossWebhookEvent::getId).toList();
    }

    public void process(Long eventId) {
        TossWebhookEvent event = tossWebhookEventRepository.findById(eventId).orElse(null);
        if (event == null || !event.isProcessable()) return;   // 그 사이에 처리된 건

        LocalDateTime now = LocalDateTime.now();

        if (!SUPPORTED_EVENT_TYPES.contains(event.getEventType())) {
            event.markIgnored(now, "처리하지 않는 이벤트: " + event.getEventType());
            return;
        }
        if (event.getPaymentKey() == null && event.getOrderId() == null) {
            event.markIgnored(now, "결제 키와 주문번호가 모두 없음");
            return;
        }

        TossPaymentResponse remote;
        try {
            remote = findRemote(event);
        } catch (PgApiException e) {
            if (e.isCode(NOT_FOUND_PAYMENT)) {
                log.warn("[토스 웹훅] 토스에 없는 결제. eventId={}, orderId={}", eventId, event.getOrderId());
                event.markIgnored(now, "토스에 없는 결제");
                return;
            }
            throw e;   // 통신 오류 등: 호출하는 쪽이 FAILED 로 기록하고 다음에 재시도
        }

        Charge charge = chargeRepository.findByPgOrderId(remote.orderId()).orElse(null);
        if (charge == null) {
            log.error("[토스 웹훅] 우리 충전 건이 없는 결제. eventId={}, orderId={}, tossStatus={}",
                    eventId, remote.orderId(), remote.status());
            event.markFailed("충전 건 없음: orderId=" + remote.orderId() + ", tossStatus=" + remote.status());
            return;
        }

        switch (charge.getStatus()) {
            case REQUESTED -> handleRequested(event, charge, remote, now);
            case CHARGED, USED -> handleCharged(event, charge, remote, now);
            case CANCELED, ABORTED -> handleClosed(event, charge, remote, now);
        }
    }

    // 처리 중 예외로 롤백된 건의 실패 기록 (별도 트랜잭션)
    public void recordFailure(Long eventId, String error) {
        tossWebhookEventRepository.findById(eventId).ifPresent(event -> {
            event.markFailed(error);
            if (event.getRetryCount() >= MAX_RETRY) {
                log.error("[토스 웹훅] 재시도 한도 초과, 수동 확인 필요. eventId={}, orderId={}, error={}",
                        eventId, event.getOrderId(), error);
            }
        });
    }


    private TossPaymentResponse findRemote(TossWebhookEvent event) {
        if (event.getPaymentKey() != null) {
            return toss.findByPaymentKey(event.getPaymentKey());
        }
        return toss.findByOrderId(event.getOrderId());
    }

    private void handleRequested(TossWebhookEvent event, Charge charge, TossPaymentResponse remote, LocalDateTime now) {
        if (charge.getCreatedAt().isAfter(now.minus(APPROVAL_GRACE))) {
            return;   // 처리 상태를 바꾸지 않고 다음 처리 때 다시 본다
        }
        if (remote.isDone() && remote.totalAmount() != charge.getAmount()) {
            log.error("[토스 웹훅] 승인 금액 불일치. eventId={}, chargeId={}, 토스={}, 충전={}",
                    event.getId(), charge.getId(), remote.totalAmount(), charge.getAmount());
            event.markFailed("승인 금액 불일치: 토스=" + remote.totalAmount() + ", 충전=" + charge.getAmount());
            return;
        }
        if (remote.isDone() || remote.isDead() || remote.isCanceled()) {
            boolean changed = chargeRecoverySupport.recover(charge, remote);
            if (changed) {
                event.markProcessed(now);
            } else {
                event.markIgnored(now, "이미 처리된 충전 건");
            }
            return;
        }
        event.markIgnored(now, "아직 결제 진행 중: tossStatus=" + remote.status());
    }

    private void handleCharged(TossWebhookEvent event, Charge charge, TossPaymentResponse remote, LocalDateTime now) {
        if (remote.isCanceled()) {
            log.error("[토스 웹훅] 외부 취소 감지, 수동 확인 필요. eventId={}, chargeId={}, chargeStatus={}, tossStatus={}",
                    event.getId(), charge.getId(), charge.getStatus(), remote.status());
            event.markFailed("외부 취소: 토스=" + remote.status() + ", 충전=" + charge.getStatus());
            return;
        }
        event.markIgnored(now, "이미 반영됨: tossStatus=" + remote.status());
    }

    private void handleClosed(TossWebhookEvent event, Charge charge, TossPaymentResponse remote, LocalDateTime now) {
        if (remote.isDone()) {
            log.error("[토스 웹훅] 토스는 결제 완료인데 충전 건은 닫혀 있음, 수동 확인 필요. eventId={}, chargeId={}, chargeStatus={}",
                    event.getId(), charge.getId(), charge.getStatus());
            event.markFailed("상태 불일치: 토스=DONE, 충전=" + charge.getStatus());
            return;
        }
        event.markIgnored(now, "이미 반영됨: tossStatus=" + remote.status());
    }
}
