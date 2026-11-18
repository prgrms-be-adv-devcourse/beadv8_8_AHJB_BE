package com.jjinmak.back.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.toss.TossPaymentsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static com.jjinmak.back.payment.app.support.PgApprovalSupport.METHOD_NOT_ALLOWED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_METHOD_NOT_ALLOWED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PG_NOT_APPROVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 토스 승인의 결제수단 검증. 예치금 충전은 계좌이체로 승인된 결제만 받아들이고, 다른 수단이면 토스 결제를 취소한다.
 */
class PgApprovalSupportTest {

    private static final long CHARGE_ID = 11L;
    private static final long AMOUNT = 30_000;
    private static final String PAYMENT_KEY = "pk-test";

    private TossPaymentsClient toss;
    private ChargeFailureRecorder failureRecorder;
    private PgApprovalSupport support;

    private Charge charge;

    @BeforeEach
    void setUp() {
        toss = mock(TossPaymentsClient.class);
        failureRecorder = mock(ChargeFailureRecorder.class);
        support = new PgApprovalSupport(toss, failureRecorder);

        charge = Charge.requestManual(new Wallet(null, WalletType.USER), 1L, AMOUNT);
        ReflectionTestUtils.setField(charge, "id", CHARGE_ID);
    }

    private TossPaymentResponse approved(String method) {
        return new TossPaymentResponse(PAYMENT_KEY, charge.getPgOrderId(), "찐막 예치금 충전", "DONE",
                AMOUNT, AMOUNT, method, null, "2026-11-18T10:00:00+09:00", null, null, null);
    }

    private void tossApprovesWith(String method) {
        when(toss.confirm(eq(PAYMENT_KEY), eq(charge.getPgOrderId()), eq(AMOUNT), anyString())).thenReturn(approved(method));
    }

    @Test
    @DisplayName("계좌이체로 승인되면 승인 응답을 그대로 돌려주고 취소하지 않는다")
    void transferIsApproved() {
        tossApprovesWith("계좌이체");

        TossPaymentResponse response = support.approve(charge, PAYMENT_KEY);

        assertThat(response.method()).isEqualTo("계좌이체");
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
        verify(failureRecorder, never()).recordAborted(anyLong(), any(), any());
    }

    @Test
    @DisplayName("카드로 승인되면 토스 결제를 취소하고 충전 건을 닫은 뒤 CHARGE_METHOD_NOT_ALLOWED")
    void cardIsCanceledAndRejected() {
        tossApprovesWith("카드");

        assertThatThrownBy(() -> support.approve(charge, PAYMENT_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_METHOD_NOT_ALLOWED);

        verify(toss).cancel(eq(PAYMENT_KEY), anyString(), contains(charge.getPgOrderId()));
        verify(failureRecorder).recordAborted(eq(CHARGE_ID), eq(METHOD_NOT_ALLOWED), contains("카드"));
    }

    @Test
    @DisplayName("간편결제로 승인돼도 취소하고 거절한다 (페이머니도 예치금 충전 수단이 아니다)")
    void easyPayIsCanceledAndRejected() {
        tossApprovesWith("간편결제");

        assertThatThrownBy(() -> support.approve(charge, PAYMENT_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_METHOD_NOT_ALLOWED);

        verify(toss).cancel(eq(PAYMENT_KEY), anyString(), anyString());
        verify(failureRecorder).recordAborted(eq(CHARGE_ID), eq(METHOD_NOT_ALLOWED), contains("간편결제"));
    }

    @Test
    @DisplayName("결제수단이 응답에 없으면 확인할 수 없으므로 취소하고 거절한다")
    void missingMethodIsRejected() {
        tossApprovesWith(null);

        assertThatThrownBy(() -> support.approve(charge, PAYMENT_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_METHOD_NOT_ALLOWED);

        verify(toss).cancel(eq(PAYMENT_KEY), anyString(), anyString());
    }

    @Test
    @DisplayName("토스 취소가 실패하면 충전 건을 닫지 않는다. 토스에 결제가 남아 있으므로 복구 스케줄러가 다시 취소해야 한다")
    void cancelFailureLeavesChargeOpen() {
        tossApprovesWith("카드");
        when(toss.cancel(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("토스 통신 실패"));

        assertThatThrownBy(() -> support.approve(charge, PAYMENT_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_METHOD_NOT_ALLOWED);

        verify(failureRecorder, never()).recordAborted(anyLong(), any(), any());
    }

    @Test
    @DisplayName("금액이 다른 승인은 결제수단보다 먼저 걸러진다 (기존 불일치 처리 그대로 PG_NOT_APPROVED)")
    void amountMismatchComesFirst() {
        when(toss.confirm(eq(PAYMENT_KEY), eq(charge.getPgOrderId()), eq(AMOUNT), anyString()))
                .thenReturn(new TossPaymentResponse(PAYMENT_KEY, charge.getPgOrderId(), "찐막 예치금 충전", "DONE",
                        AMOUNT - 1, AMOUNT - 1, "카드", null, "2026-11-18T10:00:00+09:00", null, null, null));

        assertThatThrownBy(() -> support.approve(charge, PAYMENT_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PG_NOT_APPROVED);

        verify(failureRecorder, never()).recordAborted(anyLong(), eq(METHOD_NOT_ALLOWED), any());
    }

    @Test
    @DisplayName("보상 취소는 성공하면 true, 실패하면 예외를 삼키고 false 를 돌려준다")
    void cancelApprovedReportsResult() {
        assertThat(support.cancelApproved(charge, PAYMENT_KEY, "사유")).isTrue();

        when(toss.cancel(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("토스 통신 실패"));
        assertThat(support.cancelApproved(charge, PAYMENT_KEY, "사유")).isFalse();
    }
}
