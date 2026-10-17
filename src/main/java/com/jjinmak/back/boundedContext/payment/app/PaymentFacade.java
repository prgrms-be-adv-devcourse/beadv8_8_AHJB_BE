package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentDetailDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentQuoteDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.shared.payment.dto.PaymentConfirmDto;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 토스 호출이 끼는 결제 흐름의 진입점. (API 와 PaymentPort 가 호출)
 * 수동 충전 : requestCharge → [결제창] → confirmCharge, 취소는 cancelCharge
 * 주문 결제 : requestPayment → (부족분이 있으면 [결제창]) → confirmPayment
 *
 * 트랜잭션은 여기서 연다. 유스케이스 안에서 토스 HTTP 호출도 같은 트랜잭션 안에서 일어나므로
 * 토스가 승인한 뒤 DB 반영이 실패하면 유스케이스가 토스 취소로 보상하고 예외를 올려 롤백한다.
 *
 * 지갑 낙관적 락 충돌(OptimisticLockingFailureException)은 커밋 시점에 나므로, 재시도는 이 바깥(in/ 어댑터)에서 WalletRetrySupport 로 감싼다.
 */
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final ChargeRequestUseCase chargeRequestUseCase;
    private final ChargeConfirmUseCase chargeConfirmUseCase;
    private final ChargeCancelUseCase chargeCancelUseCase;
    private final PaymentRequestUseCase paymentRequestUseCase;
    private final PaymentConfirmUseCase paymentConfirmUseCase;
    private final ChargeRecoveryUseCase chargeRecoveryUseCase;
    private final WalletGetBalanceUseCase walletGetBalanceUseCase;
    private final PaymentGetHistoryUseCase paymentGetHistoryUseCase;
    private final ChargeGetHistoryUseCase chargeGetHistoryUseCase;
    private final PaymentQuoteUseCase paymentQuoteUseCase;

    // 수동 충전

    @Transactional
    public ChargeSummaryDto requestCharge(Long holderId, long amount) {
        return chargeRequestUseCase.requestManual(holderId, amount);
    }

    @Transactional
    public ChargeSummaryDto confirmCharge(Long holderId, String pgOrderId, String paymentKey, long amount) {
        return chargeConfirmUseCase.confirm(holderId, pgOrderId, paymentKey, amount);
    }

    @Transactional
    public ChargeSummaryDto cancelCharge(Long holderId, Long chargeId) {
        return chargeCancelUseCase.cancel(holderId, chargeId);
    }

    // 주문 결제 (PaymentPort)

    @Transactional
    public PaymentResultDto requestPayment(PaymentRequestDto dto) {
        return paymentRequestUseCase.request(dto);
    }

    @Transactional
    public PaymentResultDto confirmPayment(PaymentConfirmDto dto) {
        return paymentConfirmUseCase.confirm(dto);
    }

    // 조회

    @Transactional(readOnly = true)
    public PageDto<PaymentSummaryDto> listPayments(Long holderId, int page, int size) {
        return paymentGetHistoryUseCase.list(holderId, page, size);
    }

    @Transactional(readOnly = true)
    public PaymentDetailDto getPayment(Long holderId, Long paymentId) {
        return paymentGetHistoryUseCase.detail(holderId, paymentId);
    }

    @Transactional(readOnly = true)
    public PageDto<ChargeSummaryDto> listCharges(Long holderId, int page, int size) {
        return chargeGetHistoryUseCase.listManual(holderId, page, size);
    }

    @Transactional(readOnly = true)
    public ChargeSummaryDto getCharge(Long holderId, Long chargeId) {
        return chargeGetHistoryUseCase.detail(holderId, chargeId);
    }

    @Transactional(readOnly = true)
    public PaymentQuoteDto quote(Long holderId, long amount) {
        return paymentQuoteUseCase.quote(holderId, amount);
    }

    // 복구 (PaymentScheduler)

    @Transactional(readOnly = true)
    public List<Long> findStaleRequestedChargeIds() {
        return chargeRecoveryUseCase.findStaleRequestedIds();
    }

    @Transactional(readOnly = true)
    public List<Long> findChargedButUnusedChargeIds() {
        return chargeRecoveryUseCase.findChargedButUnusedIds();
    }

    @Transactional
    public void recoverStaleCharge(Long chargeId) {
        chargeRecoveryUseCase.recover(chargeId);
    }
}
