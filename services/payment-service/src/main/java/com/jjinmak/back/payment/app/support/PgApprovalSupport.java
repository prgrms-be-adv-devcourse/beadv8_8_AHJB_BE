package com.jjinmak.back.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.exception.PgApiException;
import com.jjinmak.back.payment.out.toss.TossPaymentsClient;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_METHOD_NOT_ALLOWED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PG_NOT_APPROVED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PG_UNAVAILABLE;
import static com.jjinmak.back.payment.out.toss.TossPaymentsClient.ALREADY_PROCESSED_PAYMENT;


@Slf4j
@Component
@RequiredArgsConstructor
public class PgApprovalSupport {


    private static final String CONFIRM_KEY_PREFIX = "charge-confirm-";
    private static final String CANCEL_KEY_PREFIX = "charge-cancel-";

    // 허용되지 않은 결제수단으로 닫힌 충전의 실패 코드 · 사유 (Charge.failureCode / failureMessage)
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String METHOD_NOT_ALLOWED_REASON = "허용되지 않은 결제수단으로 결제되어 자동 취소";

    private final TossPaymentsClient toss;
    private final ChargeFailureRecorder failureRecorder;

    /**
     * 토스 승인. 응답이 Charge 의 pgOrderId·amount 와 일치하는 DONE 인지까지 검증한다.
     *
     *  - 이미 승인된 paymentKey(ALREADY_PROCESSED_PAYMENT)면 조회로 대체 (승인 응답을 못 받고 재시도한 경우)
     *  - 승인 실패(4xx)면 Charge 를 ABORTED 로 기록(별도 트랜잭션)하고 예외
     *  - 네트워크 오류면 상태를 알 수 없으므로 기록 없이 예외 (복구 스케줄러가 조회해 정리)
     *  - 응답이 Charge 와 다르면 예외. 토스가 DONE 이면 자동 취소하되, 승인 요청한 바로 그 결제일 때만 한다
     *    (주문번호나 결제 키가 다른 결제를 돌려받았으면 취소하지 않고 로그만 남긴다)
     *  - 결제수단이 이 충전에 허용된 수단(예치금 충전은 계좌이체)이 아니면 자동 취소하고 예외.
     *    결제창을 어떤 수단으로 열지는 클라이언트가 정하므로, 실제 수단은 승인 응답으로만 확인할 수 있다
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
                if (isRequestedPayment(charge, paymentKey, response)) {
                    cancelApproved(charge, response.paymentKey(), "승인 내용 불일치로 자동 취소");
                } else {
                    // 토스가 돌려준 결제가 승인 요청한 결제가 아니다. 취소하면 다른 정상 결제가 취소되므로 건드리지 않는다
                    log.error("토스가 돌려준 결제가 승인 요청한 결제와 달라 자동 취소하지 않음. chargeId={} "
                                    + "requested[orderId='{}' paymentKey={}] toss[orderId='{}' paymentKey={}]",
                            charge.getId(), charge.getPgOrderId(), paymentKey, response.orderId(), response.paymentKey());
                }
            } else {
                failureRecorder.recordAborted(charge.getId(), response.status(), response.failureMessage());
            }
            throw new BusinessException(PG_NOT_APPROVED);
        }
        if (!charge.allowsMethod(response.method())) {
            rejectMethod(charge, response);
        }
        return response;
    }

    /**
     * 허용되지 않은 결제수단으로 승인된 결제를 취소하고 예외를 던진다.
     * 취소에 성공했을 때만 Charge 를 ABORTED 로 닫는다. 취소에 실패하면 토스에는 결제가 남아 있으므로
     * REQUESTED 로 두고 복구 스케줄러가 같은 검증으로 다시 취소하게 한다.
     */
    private void rejectMethod(Charge charge, TossPaymentResponse response) {
        log.warn("허용되지 않은 결제수단으로 승인됨. chargeId={} purpose={} required={} actual='{}'",
                charge.getId(), charge.getPurpose(), charge.getRequiredMethod(), response.method());
        boolean canceled = cancelApproved(charge, response.paymentKey(), METHOD_NOT_ALLOWED_REASON);
        if (canceled) {
            failureRecorder.recordAborted(charge.getId(), METHOD_NOT_ALLOWED,
                    METHOD_NOT_ALLOWED_REASON + " (결제수단: " + response.method() + ")");
        }
        throw new BusinessException(CHARGE_METHOD_NOT_ALLOWED);
    }

    // 토스가 돌려준 결제가 서버 쪽에서 취소 요청을 보낸 결제 건이 맞는지 검증한다.
    private boolean isRequestedPayment(Charge charge, String requestedPaymentKey, TossPaymentResponse response) {
        return Objects.equals(charge.getPgOrderId(), response.orderId())
                && Objects.equals(requestedPaymentKey, response.paymentKey());
    }

    /**
     * 토스는 승인됐는데 우리 DB 반영이 실패했을 때 토스 결제를 취소한다.
     * 호출한 트랜잭션은 롤백되므로 Charge 는 REQUESTED 로 남고, 복구 스케줄러가 토스 상태(CANCELED)를 보고 ABORTED 로 닫는다.
     * 취소마저 실패하면 로그만 남긴다. 역시 복구 스케줄러 대상.
     *
     * 토스 취소에 성공했으면 true
     */
    public boolean cancelApproved(Charge charge, String paymentKey, String reason) {
        try {
            toss.cancel(paymentKey, reason, CANCEL_KEY_PREFIX + charge.getPgOrderId());
            return true;
        } catch (RuntimeException ex) {
            log.error("보상(토스 취소) 실패. chargeId={} paymentKey={} — 복구 스케줄러 대상", charge.getId(), paymentKey, ex);
            return false;
        }
    }

    // 사용자 요청 충전 취소 (전액). 실패하면 예외를 그대로 올린다
    public void cancel(Charge charge, String reason) {
        toss.cancel(charge.getPaymentKey(), reason, CANCEL_KEY_PREFIX + charge.getPgOrderId());
    }
}
