package com.jjinmak.back.payment.app.facade;


import com.jjinmak.back.payment.app.usecase.*;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


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
    public com.jjinmak.back.payment.app.dto.ChargeSummaryDto requestCharge(Long holderId, long amount) {
        return chargeRequestUseCase.requestManual(holderId, amount);
    }

    @Transactional
    public com.jjinmak.back.payment.app.dto.ChargeSummaryDto confirmCharge(Long holderId, String pgOrderId, String paymentKey, long amount) {
        return chargeConfirmUseCase.confirm(holderId, pgOrderId, paymentKey, amount);
    }

    @Transactional
    public com.jjinmak.back.payment.app.dto.ChargeSummaryDto cancelCharge(Long holderId, Long chargeId) {
        return chargeCancelUseCase.cancel(holderId, chargeId);
    }

    // 주문 결제 (PaymentPort)

    @Transactional
    public PaymentResultDto requestPayment(PaymentRequestDto dto) {
        return paymentRequestUseCase.request(dto);
    }

    @Transactional
    public com.jjinmak.back.payment.app.dto.PaymentSummaryDto confirmPayment(Long holderId, String pgOrderId, String paymentKey, long amount) {
        return paymentConfirmUseCase.confirm(holderId, pgOrderId, paymentKey, amount);
    }

    // 조회

    @Transactional(readOnly = true)
    public com.jjinmak.back.payment.app.dto.PageDto<com.jjinmak.back.payment.app.dto.PaymentSummaryDto> listPayments(Long holderId, int page, int size) {
        return paymentGetHistoryUseCase.list(holderId, page, size);
    }

    @Transactional(readOnly = true)
    public com.jjinmak.back.payment.app.dto.PaymentDetailDto getPayment(Long holderId, Long paymentId) {
        return paymentGetHistoryUseCase.detail(holderId, paymentId);
    }

    @Transactional(readOnly = true)
    public com.jjinmak.back.payment.app.dto.PageDto<com.jjinmak.back.payment.app.dto.ChargeSummaryDto> listCharges(Long holderId, int page, int size) {
        return chargeGetHistoryUseCase.listManual(holderId, page, size);
    }

    @Transactional(readOnly = true)
    public com.jjinmak.back.payment.app.dto.ChargeSummaryDto getCharge(Long holderId, Long chargeId) {
        return chargeGetHistoryUseCase.detail(holderId, chargeId);
    }

    @Transactional(readOnly = true)
    public com.jjinmak.back.payment.app.dto.PaymentQuoteDto quote(Long holderId, long amount, String payMethod, long pointUseAmount) {
        return paymentQuoteUseCase.quote(holderId, amount, payMethod, pointUseAmount);
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
