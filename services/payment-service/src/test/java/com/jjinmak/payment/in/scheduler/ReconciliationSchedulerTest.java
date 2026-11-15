package com.jjinmak.payment.in.scheduler;

import com.jjinmak.payment.app.facade.ReconciliationFacade;
import com.jjinmak.payment.app.support.PgReconciliationMatcher;
import com.jjinmak.payment.app.support.PgTransactionCollector;
import com.jjinmak.payment.app.support.ReconciliationRunRecorder;
import com.jjinmak.payment.app.usecase.ReconciliationLedgerUseCase;
import com.jjinmak.payment.app.usecase.ReconciliationPgUseCase;
import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.ReconciliationMismatch;
import com.jjinmak.payment.domain.ReconciliationRun;
import com.jjinmak.payment.domain.enums.MismatchStatus;
import com.jjinmak.payment.domain.enums.ReconciliationRunStatus;
import com.jjinmak.payment.exception.PgApiException;
import com.jjinmak.payment.support.ChargeStore;
import com.jjinmak.payment.support.FakeTossPaymentsClient;
import com.jjinmak.payment.support.ReconciliationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.payment.domain.enums.ChargeStatus.ABORTED;
import static com.jjinmak.payment.domain.enums.ChargeStatus.CANCELED;
import static com.jjinmak.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.payment.domain.enums.MismatchType.PG_APPROVED_NOT_REFLECTED;
import static com.jjinmak.payment.domain.enums.MismatchType.PG_CANCELED_NOT_REFLECTED;
import static com.jjinmak.payment.domain.enums.MismatchType.PG_ONLY;
import static com.jjinmak.payment.exception.PaymentErrorCode.PG_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PG 대사 1회의 전체 흐름: 스케줄러 → 파사드 → 유스케이스 → 실행 기록.
 * 토스와 DB 만 가짜로 바꾸고 나머지는 실제 객체를 엮어서, 시작 · 대조 · 종료 · 실패 기록이 이어지는지 본다.
 */
class ReconciliationSchedulerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 21);
    private static final LocalDateTime ON_TARGET_DATE = TARGET_DATE.atTime(15, 0);

    private FakeTossPaymentsClient toss;
    private ChargeStore charges;
    private ReconciliationStore store;
    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        toss = new FakeTossPaymentsClient();
        charges = new ChargeStore();
        store = new ReconciliationStore();
        ReconciliationRunRecorder recorder = new ReconciliationRunRecorder(store.runRepository, store.mismatchRepository);
        ReconciliationPgUseCase useCase = new ReconciliationPgUseCase(
                new PgTransactionCollector(toss),
                new PgReconciliationMatcher(),
                recorder,
                charges.repository);
        // 이 테스트는 PG 대사만 본다. 원장 대사는 검사 없이 비워 둔다
        ReconciliationLedgerUseCase ledgerUseCase = new ReconciliationLedgerUseCase(List.of(), recorder);
        scheduler = new ReconciliationScheduler(new ReconciliationFacade(useCase, ledgerUseCase));
    }

    @Test
    @DisplayName("모두 일치하면 MATCHED 로 닫고, 같은 날을 다시 돌려도 토스를 호출하지 않는다")
    void matchedThenSkipped() {
        toss.approved("A", 20_000);
        charges.add("A", CHARGED, 20_000, ON_TARGET_DATE, null);

        scheduler.reconcilePg(TARGET_DATE);

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(run.getCheckedCount()).isEqualTo(1);
        assertThat(store.mismatches).isEmpty();

        scheduler.reconcilePg(TARGET_DATE);

        assertThat(toss.requestedCursors()).hasSize(1);
        assertThat(store.runs).hasSize(1);
        assertThat(run.getAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("거래가 없는 날은 비교 0건으로 MATCHED")
    void emptyDay() {
        scheduler.reconcilePg(TARGET_DATE);

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(run.getCheckedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("불일치가 있으면 저장하고 MISMATCHED 로 닫는다")
    void mismatchesAreRecorded() {
        toss.approved("MATCHED", 20_000)
                .approved("CANCELED-AT-TOSS", 100_000).canceled("CANCELED-AT-TOSS", 100_000)
                .approved("APPROVED-AT-TOSS", 300_000)
                .approved("UNKNOWN-TO-US", 1_000);
        toss.payment("CANCELED-AT-TOSS", "CANCELED", 100_000, 0)
                .payment("APPROVED-AT-TOSS", "DONE", 300_000, 300_000)
                .payment("UNKNOWN-TO-US", "DONE", 1_000, 1_000);
        charges.add("MATCHED", CHARGED, 20_000, ON_TARGET_DATE, null);
        charges.add("CANCELED-AT-TOSS", CHARGED, 100_000, ON_TARGET_DATE, null);
        charges.add("APPROVED-AT-TOSS", ABORTED, 300_000);

        scheduler.reconcilePg(TARGET_DATE);

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MISMATCHED);
        assertThat(run.getCheckedCount()).isEqualTo(4);
        assertThat(run.getMismatchCount()).isEqualTo(3);
        assertThat(store.mismatches).hasSize(3);
        assertThat(store.mismatchOf("MATCHED")).isNull();
        assertThat(store.mismatchOf("CANCELED-AT-TOSS").getType()).isEqualTo(PG_CANCELED_NOT_REFLECTED);
        assertThat(store.mismatchOf("APPROVED-AT-TOSS").getType()).isEqualTo(PG_APPROVED_NOT_REFLECTED);
        assertThat(store.mismatchOf("UNKNOWN-TO-US").getType()).isEqualTo(PG_ONLY);
        assertThat(store.mismatchOf("CANCELED-AT-TOSS").getStatus()).isEqualTo(MismatchStatus.OPEN);
    }

    @Test
    @DisplayName("불일치를 바로잡고 다시 돌리면 그 건은 자동으로 닫히고, 남은 게 없으면 MATCHED 가 된다")
    void rerunAfterFix() {
        toss.approved("A", 100_000).canceled("A", 100_000);
        toss.payment("A", "CANCELED", 100_000, 0);
        Charge charge = charges.add("A", CHARGED, 100_000, ON_TARGET_DATE, null);
        scheduler.reconcilePg(TARGET_DATE);
        ReconciliationRun run = store.runs.get(0);
        ReconciliationMismatch mismatch = store.mismatchOf("A");
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MISMATCHED);

        ReflectionTestUtils.setField(charge, "status", CANCELED);      // 운영자가 충전 건을 취소로 맞춤
        scheduler.reconcilePg(TARGET_DATE);

        assertThat(run.getAttempt()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(run.getMismatchCount()).isEqualTo(0);
        assertThat(mismatch.getStatus()).isEqualTo(MismatchStatus.RESOLVED);
        assertThat(mismatch.getResolvedBy()).isEqualTo(ReconciliationMismatch.RESOLVED_BY_SYSTEM);
        assertThat(store.mismatches).hasSize(1);
    }

    @Test
    @DisplayName("토스 거래 조회가 실패하면 FAILED 로 닫고 사유를 남긴다. 다음에 돌리면 같은 행으로 재실행한다")
    void failedThenRecovered() {
        toss.approved("A", 20_000);
        charges.add("A", CHARGED, 20_000, ON_TARGET_DATE, null);
        toss.failTransactions(new PgApiException(PG_UNAVAILABLE, "NETWORK", "timeout"));

        scheduler.reconcilePg(TARGET_DATE);

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.FAILED);
        assertThat(run.getFailureReason()).startsWith("PgApiException: ");
        assertThat(run.getFailureReason()).contains("NETWORK");

        toss.failTransactions(null);
        scheduler.reconcilePg(TARGET_DATE);

        assertThat(store.runs).hasSize(1);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(run.getAttempt()).isEqualTo(2);
        assertThat(run.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("2차 확정의 단건 조회가 실패해도 FAILED 로 닫고, 불일치는 하나도 저장하지 않는다")
    void failedDuringConfirmation() {
        toss.approved("A", 300_000);
        toss.failPayments(new PgApiException(PG_UNAVAILABLE, "NETWORK", "timeout"));
        charges.add("A", ABORTED, 300_000);

        scheduler.reconcilePg(TARGET_DATE);

        assertThat(store.runs.get(0).getStatus()).isEqualTo(ReconciliationRunStatus.FAILED);
        assertThat(store.mismatches).isEmpty();
    }

    @Test
    @DisplayName("다른 서버가 먼저 시작해 유니크 제약 위반이 나면 건너뛴다. 토스를 호출하지 않는다")
    void skipsWhenAnotherServerStartedFirst() {
        store.conflictOnSave();

        scheduler.reconcilePg(TARGET_DATE);

        assertThat(store.runs).isEmpty();
        assertThat(toss.requestedCursors()).isEmpty();
    }
}
