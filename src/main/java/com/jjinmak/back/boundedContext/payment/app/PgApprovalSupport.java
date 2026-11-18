package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.exception.PgApiException;
import com.jjinmak.back.boundedContext.payment.out.TossPaymentsClient;
import com.jjinmak.back.boundedContext.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.PG_NOT_APPROVED;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.PG_UNAVAILABLE;
import static com.jjinmak.back.boundedContext.payment.out.TossPaymentsClient.ALREADY_PROCESSED_PAYMENT;

/**
 * 토스 승인/보상 공통 로직. 충전 승인과 주문 결제 승인 두 유스케이스가 같이 쓴다.
 * UseCase에서 사용하는 지원 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgApprovalSupport {

    /**
     * 토스 Idempotency-Key 접두사. 뒤에는 DB id 가 아니라 pgOrderId(UUID 기반) 를 붙인다.
     * 토스는 같은 키의 요청에 첫 응답을 15일간 그대로 돌려주는데, DB id 는 로컬·서버가 같은 테스트 상점을 쓰거나
     * create-drop 으로 DB 가 초기화되면 다시 1부터 시작해 예전 결제의 응답이 돌아오는 사고가 난다.
     */
    private static final String CONFIRM_KEY_PREFIX = "charge-confirm-";
    private static final String CANCEL_KEY_PREFIX = "charge-cancel-";

    private final TossPaymentsClient toss;
    private final ChargeFailureRecorder failureRecorder;

    /**
     * 토스 승인. 응답이 Charge 의 pgOrderId·amount 와 일치하는 DONE 인지까지 검증한다.
     *
     *  - 이미 승인된 paymentKey(ALREADY_PROCESSED_PAYMENT)면 조회로 대체 (승인 응답을 못 받고 재시도한 경우)
     *  - 승인 실패(4xx)면 Charge 를 ABORTED 로 기록(별도 트랜잭션)하고 예외
     *  - 네트워크 오류면 상태를 알 수 없으므로 기록 없이 예외 (복구 스케줄러가 조회해 정리)
     */
    public TossPaymentResponse approve(Charge charge, String paymentKey) {
        TossPaymentResponse response;
        try {
            response = toss.confirm(paymentKey, charge.getPgOrderId(), charge.getAmount(), CONFIRM_KEY_PREFIX + charge.getPgOrderId());
        } catch (PgApiException e) {
            if (e.isCode(ALREADY_PROCESSED_PAYMENT)) {
                response = toss.findByPaymentKey(paymentKey);
            } else if (e.getErrorCode() == PG_UNAVAILABLE) {
                throw e;
            } else {
                failureRecorder.recordAborted(charge.getId(), e.getPgCode(), e.getPgMessage());
                throw e;
            }
        }

        boolean statusMismatch = !response.isDone();
        boolean orderIdMismatch = !charge.getPgOrderId().equals(response.orderId());
        boolean amountMismatch = response.totalAmount() != charge.getAmount();
        if (statusMismatch || orderIdMismatch || amountMismatch) {
            // 어느 조건에서 걸렸는지와 비교 상대(DB 의 Charge 값)를 함께 남긴다. 토스 값만 찍으면 원인을 못 찾는다
            log.error("토스 승인 응답이 요청과 다름. chargeId={} mismatch[status={} orderId={} amount={}] "
                            + "toss[status={} orderId='{}' amount={} paymentKey={}] charge[status={} orderId='{}' amount={}]",
                    charge.getId(), statusMismatch, orderIdMismatch, amountMismatch,
                    response.status(), response.orderId(), response.totalAmount(), response.paymentKey(),
                    charge.getStatus(), charge.getPgOrderId(), charge.getAmount());
            if (response.isDone()) {
                cancelApproved(charge, response.paymentKey(), "승인 내용 불일치로 자동 취소");
            } else {
                failureRecorder.recordAborted(charge.getId(), response.status(), response.failureMessage());
            }
            throw new BusinessException(PG_NOT_APPROVED);
        }
        return response;
    }

    /**
     * 보상: 토스는 승인됐는데 우리 DB 반영이 실패했을 때 토스 결제를 취소한다.
     * 호출한 트랜잭션은 롤백되므로 Charge 는 REQUESTED 로 남고, 복구 스케줄러가 토스 상태(CANCELED)를 보고 ABORTED 로 닫는다.
     * 취소마저 실패하면 로그만 남긴다. 역시 복구 스케줄러 대상.
     */
    public void cancelApproved(Charge charge, String paymentKey, String reason) {
        try {
            toss.cancel(paymentKey, reason, CANCEL_KEY_PREFIX + charge.getPgOrderId());
        } catch (RuntimeException ex) {
            log.error("보상(토스 취소) 실패. chargeId={} paymentKey={} — 복구 스케줄러 대상", charge.getId(), paymentKey, ex);
        }
    }

    /** 사용자 요청 충전 취소 (전액). 실패하면 예외를 그대로 올린다 */
    public void cancel(Charge charge, String reason) {
        toss.cancel(charge.getPaymentKey(), reason, CANCEL_KEY_PREFIX + charge.getPgOrderId());
    }
}
