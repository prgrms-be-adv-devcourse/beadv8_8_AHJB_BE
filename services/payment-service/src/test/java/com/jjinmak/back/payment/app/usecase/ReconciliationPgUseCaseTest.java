package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.PgReconciliationResult;
import com.jjinmak.back.payment.app.support.PgReconciliationMatcher;
import com.jjinmak.back.payment.app.support.PgTransactionCollector;
import com.jjinmak.back.payment.app.support.ReconciliationRunRecorder;
import com.jjinmak.back.payment.app.usecase.ReconciliationPgUseCase;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.exception.PgApiException;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.support.ChargeStore;
import com.jjinmak.back.payment.support.FakeTossPaymentsClient;
import com.jjinmak.back.payment.support.ReconciliationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.jjinmak.back.payment.domain.enums.ChargeStatus.ABORTED;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.CANCELED;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.USED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.AMOUNT_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.INTERNAL_CANCELED_NOT_IN_PG;
import static com.jjinmak.back.payment.domain.enums.MismatchType.INTERNAL_ONLY;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_APPROVED_NOT_REFLECTED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_CANCELED_NOT_REFLECTED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_ONLY;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PG_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PG 대사의 대조 단계 (reconcile).
 *   1차 판정은 대상일의 거래 목록으로, 일치하지 않은 건만 토스 단건 조회로 2차 확정한다.
 *   여기서는 저장하지 않고 결과(비교 건수, 확정된 불일치)만 돌려준다.
 */
class ReconciliationPgUseCaseTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 21);
    private static final LocalDateTime ON_TARGET_DATE = TARGET_DATE.atTime(15, 0);
    private static final LocalDateTime NEXT_DAY_DAWN = TARGET_DATE.plusDays(1).atTime(0, 30);
    private static final LocalDateTime PREVIOUS_DAY = TARGET_DATE.minusDays(1).atTime(20, 0);

    private FakeTossPaymentsClient toss;
    private ChargeStore charges;
    private ReconciliationPgUseCase useCase;

    @BeforeEach
    void setUp() {
        toss = new FakeTossPaymentsClient();
        charges = new ChargeStore();
        ReconciliationStore store = new ReconciliationStore();
        useCase = new ReconciliationPgUseCase(
                new PgTransactionCollector(toss),
                new PgReconciliationMatcher(),
                new ReconciliationRunRecorder(store.runRepository, store.mismatchRepository),
                charges.repository);
    }

    private static DetectedMismatchDto only(PgReconciliationResult result) {
        assertThat(result.mismatches()).hasSize(1);
        return result.mismatches().get(0);
    }

    // ===== 일치 =====

    @Test
    @DisplayName("토스 승인과 충전 건이 일치하면 불일치가 없고 단건 조회도 하지 않는다")
    void matched() {
        toss.approved("A", 20_000);
        charges.add("A", CHARGED, 20_000, ON_TARGET_DATE, null);

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
        assertThat(toss.lookedUpOrderIds()).isEmpty();
    }

    @Test
    @DisplayName("승인 후 전액 취소된 결제는 충전 건이 CANCELED · ABORTED · REQUESTED 어느 쪽이어도 일치")
    void canceledPaymentsMatch() {
        toss.approved("A", 10_000).canceled("A", 10_000)
                .approved("B", 10_000).canceled("B", 10_000)
                .approved("C", 10_000).canceled("C", 10_000);
        charges.add("A", CANCELED, 10_000, ON_TARGET_DATE, ON_TARGET_DATE);
        charges.add("B", ABORTED, 10_000);
        charges.add("C", REQUESTED, 10_000);

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(3);
        assertThat(result.mismatches()).isEmpty();
        assertThat(toss.lookedUpOrderIds()).isEmpty();
    }

    @Test
    @DisplayName("날짜 경계: 승인은 대상일, 취소는 다음 날 새벽 → 단건 조회로 확인하고 일치")
    void approvedOnTargetDateCanceledNextDay() {
        toss.approved("A", 33_000);                                   // 대상일 목록에는 승인만 있다
        toss.payment("A", "CANCELED", 33_000, 0);                     // 지금은 취소된 상태
        charges.add("A", CANCELED, 33_000, ON_TARGET_DATE, NEXT_DAY_DAWN);

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);

        assertThat(result.mismatches()).isEmpty();
        assertThat(toss.lookedUpOrderIds()).containsExactly("A");
    }

    @Test
    @DisplayName("날짜 경계: 승인은 전날, 취소만 대상일 → 단건 조회로 확인하고 일치")
    void approvedPreviousDayCanceledOnTargetDate() {
        toss.canceled("A", 50_000);                                   // 대상일 목록에는 취소만 있다
        toss.payment("A", "CANCELED", 50_000, 0);
        charges.add("A", CANCELED, 50_000, PREVIOUS_DAY, ON_TARGET_DATE);

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
        assertThat(toss.lookedUpOrderIds()).containsExactly("A");
    }

    @Test
    @DisplayName("대상일 거래 목록에는 없지만 단건 조회로는 승인인 충전 건은 일치")
    void chargedButMissingFromTransactionList() {
        toss.payment("A", "DONE", 8_000, 8_000);
        charges.add("A", CHARGED, 8_000, ON_TARGET_DATE, null);

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("대상일에 승인 · 취소되지 않았고 토스 거래에도 없는 충전 건은 비교 대상이 아니다")
    void chargesOfOtherDaysAreNotChecked() {
        charges.add("OLD", CHARGED, 9_000, PREVIOUS_DAY, null);
        charges.add("REQUESTED-ONLY", REQUESTED, 9_000);

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(0);
        assertThat(result.mismatches()).isEmpty();
    }

    // ===== 불일치 =====

    @Test
    @DisplayName("토스는 취소됐는데 충전 건이 CHARGED 면 PG_CANCELED_NOT_REFLECTED")
    void pgCanceledNotReflected() {
        toss.approved("A", 100_000).canceled("A", 100_000);
        toss.payment("A", "CANCELED", 100_000, 0);
        Charge charge = charges.add("A", CHARGED, 100_000, ON_TARGET_DATE, null);

        DetectedMismatchDto mismatch = only(useCase.reconcile(TARGET_DATE));

        assertThat(mismatch.type()).isEqualTo(PG_CANCELED_NOT_REFLECTED);
        assertThat(mismatch.targetType()).isEqualTo(MismatchTargetType.CHARGE);
        assertThat(mismatch.targetKey()).isEqualTo(String.valueOf(charge.getId()));
        assertThat(mismatch.pgOrderId()).isEqualTo("A");
        assertThat(mismatch.expectedState()).isEqualTo("CANCELED");
        assertThat(mismatch.expectedAmount()).isEqualTo(0L);
        assertThat(mismatch.actualState()).isEqualTo("CHARGED");
        assertThat(mismatch.actualAmount()).isEqualTo(100_000L);
        assertThat(mismatch.detail()).startsWith(PG_CANCELED_NOT_REFLECTED.getDescription());
        assertThat(mismatch.detail()).contains("토스 승인 100000원", "토스에 남은 금액 0원", "충전 금액 100000원");
    }

    @Test
    @DisplayName("토스는 승인인데 충전 건이 ABORTED 면 PG_APPROVED_NOT_REFLECTED (우리가 인정한 금액은 0)")
    void pgApprovedNotReflected() {
        toss.approved("A", 300_000);
        toss.payment("A", "DONE", 300_000, 300_000);
        charges.add("A", ABORTED, 300_000);

        DetectedMismatchDto mismatch = only(useCase.reconcile(TARGET_DATE));

        assertThat(mismatch.type()).isEqualTo(PG_APPROVED_NOT_REFLECTED);
        assertThat(mismatch.expectedState()).isEqualTo("APPROVED");
        assertThat(mismatch.expectedAmount()).isEqualTo(300_000L);
        assertThat(mismatch.actualState()).isEqualTo("ABORTED");
        assertThat(mismatch.actualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("토스에 거래가 있는데 충전 건이 없으면 PG_ONLY. 대상을 토스 주문번호로 가리킨다")
    void pgOnly() {
        toss.approved("A", 1_000);
        toss.payment("A", "DONE", 1_000, 1_000);

        DetectedMismatchDto mismatch = only(useCase.reconcile(TARGET_DATE));

        assertThat(mismatch.type()).isEqualTo(PG_ONLY);
        assertThat(mismatch.targetType()).isEqualTo(MismatchTargetType.PG_TRANSACTION);
        assertThat(mismatch.targetKey()).isEqualTo("A");
        assertThat(mismatch.pgOrderId()).isEqualTo("A");
        assertThat(mismatch.expectedAmount()).isEqualTo(1_000L);
        assertThat(mismatch.actualState()).isNull();
        assertThat(mismatch.actualAmount()).isNull();
    }

    @Test
    @DisplayName("충전 건은 승인으로 아는데 토스에 결제 기록이 없으면 INTERNAL_ONLY")
    void internalOnly() {
        charges.add("A", CHARGED, 7_000, ON_TARGET_DATE, null);       // 토스 거래도, 단건 조회 결과도 없다

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);
        DetectedMismatchDto mismatch = only(result);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(mismatch.type()).isEqualTo(INTERNAL_ONLY);
        assertThat(mismatch.expectedState()).isEqualTo("NONE");
        assertThat(mismatch.expectedAmount()).isEqualTo(0L);
        assertThat(mismatch.actualAmount()).isEqualTo(7_000L);
        assertThat(toss.lookedUpOrderIds()).containsExactly("A");
    }

    @Test
    @DisplayName("충전 건은 취소됐는데 토스는 지금도 승인 상태면 INTERNAL_CANCELED_NOT_IN_PG")
    void internalCanceledNotInPg() {
        toss.approved("A", 5_000);
        toss.payment("A", "DONE", 5_000, 5_000);
        charges.add("A", CANCELED, 5_000, ON_TARGET_DATE, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(useCase.reconcile(TARGET_DATE));

        assertThat(mismatch.type()).isEqualTo(INTERNAL_CANCELED_NOT_IN_PG);
        assertThat(mismatch.expectedAmount()).isEqualTo(5_000L);
        assertThat(mismatch.actualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("토스 승인 금액과 충전 금액이 다르면 AMOUNT_MISMATCH")
    void amountMismatch() {
        toss.approved("A", 5_000);
        toss.payment("A", "DONE", 5_000, 5_000);
        charges.add("A", USED, 7_000, ON_TARGET_DATE, null);

        DetectedMismatchDto mismatch = only(useCase.reconcile(TARGET_DATE));

        assertThat(mismatch.type()).isEqualTo(AMOUNT_MISMATCH);
        assertThat(mismatch.expectedAmount()).isEqualTo(5_000L);
        assertThat(mismatch.actualState()).isEqualTo("USED");
        assertThat(mismatch.actualAmount()).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("2차 확정 때 충전 건을 다시 읽는다. 그 사이 상태가 맞춰졌으면 불일치로 남기지 않는다")
    void rereadsChargeBeforeConfirming() {
        toss.approved("A", 20_000);
        toss.payment("A", "DONE", 20_000, 20_000);
        Charge charge = charges.add("A", ABORTED, 20_000);

        // 1차 판정용 조회가 끝난 뒤(= 단건 조회 직전)에 충전 건이 CHARGED 로 바뀐 상황
        FakeTossPaymentsClient tossThatFixesCharge = new FakeTossPaymentsClient() {
            @Override
            public TossPaymentResponse findByOrderId(String pgOrderId) {
                ReflectionTestUtils.setField(charge, "status", CHARGED);
                return toss.findByOrderId(pgOrderId);
            }
        };
        tossThatFixesCharge.approved("A", 20_000);
        ReconciliationStore store = new ReconciliationStore();
        ReconciliationPgUseCase useCaseWithFixingToss = new ReconciliationPgUseCase(
                new PgTransactionCollector(tossThatFixesCharge),
                new PgReconciliationMatcher(),
                new ReconciliationRunRecorder(store.runRepository, store.mismatchRepository),
                charges.repository);

        PgReconciliationResult result = useCaseWithFixingToss.reconcile(TARGET_DATE);

        assertThat(result.mismatches()).isEmpty();
    }

    // ===== 그 외 =====

    @Test
    @DisplayName("주문번호가 많으면 충전 건 조회를 500개씩 나눠서 한다")
    void queriesChargesInChunks() {
        for (int i = 1; i <= 1_200; i++) {
            toss.approved("O" + i, 1_000);
            charges.add("O" + i, CHARGED, 1_000, ON_TARGET_DATE, null);
        }

        PgReconciliationResult result = useCase.reconcile(TARGET_DATE);

        assertThat(charges.inQuerySizes).containsExactly(500, 500, 200);
        assertThat(result.checkedCount()).isEqualTo(1_200);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("2차 확정의 단건 조회가 실패하면 예외를 던진다 (일부 결과만으로 끝내지 않는다)")
    void failsWhenLookupFails() {
        toss.approved("A", 300_000);
        toss.failPayments(new PgApiException(PG_UNAVAILABLE, "NETWORK", "timeout"));
        charges.add("A", ABORTED, 300_000);

        assertThatThrownBy(() -> useCase.reconcile(TARGET_DATE))
                .isInstanceOf(PgApiException.class)
                .extracting("errorCode").isEqualTo(PG_UNAVAILABLE);
    }
}
