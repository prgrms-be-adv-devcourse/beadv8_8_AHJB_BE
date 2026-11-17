package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.app.support.ReconciliationRunRecorder;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.domain.ReconciliationMismatch;
import com.jjinmak.back.payment.domain.ReconciliationRun;
import com.jjinmak.back.payment.domain.enums.MismatchStatus;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.domain.enums.MismatchType;
import com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus;
import com.jjinmak.back.payment.support.ReconciliationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.jjinmak.back.payment.domain.enums.MismatchType.AMOUNT_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.ESCROW_HOLDING_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.INTERNAL_ONLY;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_APPROVED_NOT_REFLECTED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_CANCELED_NOT_REFLECTED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationType.LEDGER;
import static com.jjinmak.back.payment.domain.enums.ReconciliationType.PG;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.MISMATCH_TYPE_NOT_ALLOWED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.RECONCILIATION_RUN_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 대사 실행 기록.
 *   start  : 같은 종류 · 대상일의 실행이 있는지에 따라 새로 시작 / 재실행 / 건너뜀
 *   finish : 이번에 발견한 불일치를 이전 불일치와 비교해 저장하고 실행을 닫는다
 *   fail   : 실행을 FAILED 로 닫는다
 */
class ReconciliationRunRecorderTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 14);

    private ReconciliationStore store;
    private ReconciliationRunRecorder recorder;

    @BeforeEach
    void setUp() {
        store = new ReconciliationStore();
        recorder = new ReconciliationRunRecorder(store.runRepository, store.mismatchRepository);
    }

    // 충전 건(chargeId)에 대한 PG 불일치 1건
    private static DetectedMismatchDto detected(MismatchType type, String chargeId) {
        return new DetectedMismatchDto(type, MismatchTargetType.CHARGE, chargeId, "CHG-" + chargeId,
                "APPROVED", 20_000L, "ABORTED", 0L, "테스트 불일치");
    }

    // 이미 있는 실행. startedAt 을 정할 수 있다
    private ReconciliationRun existingRun(LocalDate targetDate, LocalDateTime startedAt) {
        return store.save(ReconciliationRun.start(PG, targetDate, startedAt));
    }

    // ===== start =====

    @Test
    @DisplayName("실행이 없으면 새로 저장하고 RUNNING 으로 시작한다")
    void startNew() {
        Optional<Long> runId = recorder.start(PG, TARGET_DATE);

        assertThat(store.runs).hasSize(1);
        ReconciliationRun run = store.runs.get(0);
        assertThat(runId).contains(run.getId());
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.RUNNING);
        assertThat(run.getType()).isEqualTo(PG);
        assertThat(run.getTargetDate()).isEqualTo(TARGET_DATE);
        assertThat(run.getAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("대사 종류나 대상일이 다르면 별개의 실행이다")
    void startSeparatesByTypeAndDate() {
        recorder.start(PG, TARGET_DATE);

        assertThat(recorder.start(LEDGER, TARGET_DATE)).isPresent();
        assertThat(recorder.start(PG, TARGET_DATE.minusDays(1))).isPresent();
        assertThat(store.runs).hasSize(3);
    }

    @Test
    @DisplayName("방금 시작해 실행 중인 대사는 건너뛴다")
    void startSkipsRunning() {
        ReconciliationRun running = existingRun(TARGET_DATE, LocalDateTime.now().minusMinutes(59));

        assertThat(recorder.start(PG, TARGET_DATE)).isEmpty();
        assertThat(running.getAttempt()).isEqualTo(1);
        assertThat(store.runs).hasSize(1);
    }

    @Test
    @DisplayName("실행 중 상태로 1시간 넘게 멈춰 있으면 죽은 실행으로 보고 다시 시작한다")
    void startRestartsStaleRunning() {
        ReconciliationRun stale = existingRun(TARGET_DATE, LocalDateTime.now().minusHours(2));

        Optional<Long> runId = recorder.start(PG, TARGET_DATE);

        assertThat(runId).contains(stale.getId());
        assertThat(stale.getStatus()).isEqualTo(ReconciliationRunStatus.RUNNING);
        assertThat(stale.getAttempt()).isEqualTo(2);
        assertThat(stale.getStartedAt().isAfter(LocalDateTime.now().minusMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("불일치 0건으로 끝난 대사는 다시 돌리지 않는다")
    void startSkipsMatched() {
        ReconciliationRun matched = existingRun(TARGET_DATE, LocalDateTime.now());
        matched.finish(LocalDateTime.now(), 5, 0);

        assertThat(recorder.start(PG, TARGET_DATE)).isEmpty();
        assertThat(matched.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(matched.getAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("불일치가 있던 대사는 같은 행으로 재실행한다")
    void startRestartsMismatched() {
        ReconciliationRun mismatched = existingRun(TARGET_DATE, LocalDateTime.now());
        mismatched.finish(LocalDateTime.now(), 5, 2);

        Optional<Long> runId = recorder.start(PG, TARGET_DATE);

        assertThat(runId).contains(mismatched.getId());
        assertThat(mismatched.getStatus()).isEqualTo(ReconciliationRunStatus.RUNNING);
        assertThat(mismatched.getAttempt()).isEqualTo(2);
        assertThat(mismatched.getMismatchCount()).isEqualTo(0);
        assertThat(store.runs).hasSize(1);
    }

    @Test
    @DisplayName("실패한 대사는 같은 행으로 재실행하고 실패 사유를 비운다")
    void startRestartsFailed() {
        ReconciliationRun failed = existingRun(TARGET_DATE, LocalDateTime.now());
        failed.fail(LocalDateTime.now(), "토스 조회 실패");

        Optional<Long> runId = recorder.start(PG, TARGET_DATE);

        assertThat(runId).contains(failed.getId());
        assertThat(failed.getStatus()).isEqualTo(ReconciliationRunStatus.RUNNING);
        assertThat(failed.getAttempt()).isEqualTo(2);
        assertThat(failed.getFailureReason()).isNull();
    }

    // ===== finish =====

    @Test
    @DisplayName("불일치가 없으면 MATCHED 로 닫는다")
    void finishMatched() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();

        recorder.finish(runId, 7, List.of());

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(run.getCheckedCount()).isEqualTo(7);
        assertThat(run.getMismatchCount()).isEqualTo(0);
        assertThat(store.mismatches).isEmpty();
    }

    @Test
    @DisplayName("발견한 불일치를 OPEN 으로 저장하고 MISMATCHED 로 닫는다")
    void finishSavesMismatches() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();

        recorder.finish(runId, 10, List.of(detected(PG_APPROVED_NOT_REFLECTED, "1"), detected(AMOUNT_MISMATCH, "2")));

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MISMATCHED);
        assertThat(run.getCheckedCount()).isEqualTo(10);
        assertThat(run.getMismatchCount()).isEqualTo(2);
        assertThat(store.mismatches).hasSize(2);
        ReconciliationMismatch saved = store.mismatchOf("CHG-1");
        assertThat(saved.getRun()).isSameAs(run);
        assertThat(saved.getType()).isEqualTo(PG_APPROVED_NOT_REFLECTED);
        assertThat(saved.getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(saved.getTargetKey()).isEqualTo("1");
        assertThat(saved.getExpectedAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("같은 불일치가 두 번 넘어오면 한 번만 저장한다. 대상이 같아도 종류가 다르면 별개다")
    void finishDeduplicates() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();

        recorder.finish(runId, 3, List.of(
                detected(PG_APPROVED_NOT_REFLECTED, "1"),
                detected(PG_APPROVED_NOT_REFLECTED, "1"),
                detected(AMOUNT_MISMATCH, "1")));

        assertThat(store.mismatches).hasSize(2);
        assertThat(store.runs.get(0).getMismatchCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("재실행: 이전 불일치의 상태에 따라 유지 · 재오픈 · 자동 해결하고, 새로 발견한 건은 저장한다")
    void finishComparesWithPreviousMismatches() {
        LocalDateTime now = LocalDateTime.now();
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();
        recorder.finish(runId, 10, List.of(
                detected(PG_APPROVED_NOT_REFLECTED, "1"),
                detected(PG_CANCELED_NOT_REFLECTED, "2"),
                detected(PG_APPROVED_NOT_REFLECTED, "3"),
                detected(AMOUNT_MISMATCH, "4")));
        ReconciliationMismatch keptOpen = store.mismatchOf("CHG-1");
        ReconciliationMismatch resolvedButStillThere = store.mismatchOf("CHG-2");
        ReconciliationMismatch ignored = store.mismatchOf("CHG-3");
        ReconciliationMismatch resolvedAndGone = store.mismatchOf("CHG-4");
        resolvedButStillThere.resolve(now, "7", "토스에서 취소함");
        ignored.ignore(now, "7", "테스트 결제");
        resolvedAndGone.resolve(now, "7", "금액 맞춤");

        recorder.start(PG, TARGET_DATE);
        recorder.finish(runId, 12, List.of(
                detected(PG_APPROVED_NOT_REFLECTED, "1"),      // 이전에 OPEN → 그대로
                detected(PG_CANCELED_NOT_REFLECTED, "2"),      // 이전에 RESOLVED → 다시 OPEN
                detected(PG_APPROVED_NOT_REFLECTED, "3"),      // 이전에 IGNORED → 그대로, 건수 제외
                detected(INTERNAL_ONLY, "5")));                // 처음 발견 → 저장

        assertThat(keptOpen.getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(resolvedButStillThere.getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(resolvedButStillThere.getResolvedBy()).isNull();
        assertThat(resolvedButStillThere.getDetail()).contains("이전 처리: 7");
        assertThat(ignored.getStatus()).isEqualTo(MismatchStatus.IGNORED);
        assertThat(resolvedAndGone.getStatus()).isEqualTo(MismatchStatus.RESOLVED);
        assertThat(resolvedAndGone.getResolvedBy()).isEqualTo("7");
        assertThat(store.mismatches).hasSize(5);
        assertThat(store.mismatchOf("CHG-5").getType()).isEqualTo(INTERNAL_ONLY);

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getAttempt()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MISMATCHED);
        assertThat(run.getMismatchCount()).isEqualTo(3);   // 유지 1 + 재오픈 1 + 신규 1
        assertThat(run.getCheckedCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("재실행에서 발견되지 않은 OPEN 건은 SYSTEM 이 RESOLVED 로 닫는다")
    void finishAutoResolvesMissingOpenMismatches() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();
        recorder.finish(runId, 10, List.of(detected(PG_APPROVED_NOT_REFLECTED, "1"), detected(AMOUNT_MISMATCH, "2")));

        recorder.start(PG, TARGET_DATE);
        recorder.finish(runId, 10, List.of(detected(AMOUNT_MISMATCH, "2")));

        ReconciliationMismatch gone = store.mismatchOf("CHG-1");
        assertThat(gone.getStatus()).isEqualTo(MismatchStatus.RESOLVED);
        assertThat(gone.getResolvedBy()).isEqualTo(ReconciliationMismatch.RESOLVED_BY_SYSTEM);
        assertThat(store.mismatchOf("CHG-2").getStatus()).isEqualTo(MismatchStatus.OPEN);
        assertThat(store.runs.get(0).getMismatchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재실행에서 IGNORED 인 건만 다시 발견되면 불일치 0건으로 보고 MATCHED 로 닫는다")
    void finishMatchedWhenOnlyIgnoredRemains() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();
        recorder.finish(runId, 10, List.of(detected(PG_APPROVED_NOT_REFLECTED, "1")));
        store.mismatchOf("CHG-1").ignore(LocalDateTime.now(), "7", "조치 불필요");

        recorder.start(PG, TARGET_DATE);
        recorder.finish(runId, 10, List.of(detected(PG_APPROVED_NOT_REFLECTED, "1")));

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(run.getMismatchCount()).isEqualTo(0);
        assertThat(store.mismatchOf("CHG-1").getStatus()).isEqualTo(MismatchStatus.IGNORED);
        assertThat(recorder.start(PG, TARGET_DATE)).isEmpty();
    }

    @Test
    @DisplayName("원장 대사의 불일치는 주문번호 없이 저장한다")
    void finishSavesLedgerMismatch() {
        Long runId = recorder.start(LEDGER, TARGET_DATE).orElseThrow();

        recorder.finish(runId, 3, List.of(new DetectedMismatchDto(ESCROW_HOLDING_MISMATCH, MismatchTargetType.WALLET,
                "1", null, null, 150_000L, null, 140_000L, null)));

        ReconciliationMismatch saved = store.mismatches.get(0);
        assertThat(saved.getPgOrderId()).isNull();
        assertThat(saved.getTargetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(saved.getExpectedAmount()).isEqualTo(150_000L);
        assertThat(saved.getActualAmount()).isEqualTo(140_000L);
    }

    @Test
    @DisplayName("PG 대사 실행에 원장 대사의 불일치를 넘기면 거부한다")
    void finishRejectsMismatchOfOtherReconciliationType() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();
        List<DetectedMismatchDto> ledgerMismatch = List.of(new DetectedMismatchDto(ESCROW_HOLDING_MISMATCH,
                MismatchTargetType.WALLET, "1", null, null, 1L, null, 2L, null));

        assertThatThrownBy(() -> recorder.finish(runId, 1, ledgerMismatch))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(MISMATCH_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("없는 실행을 닫으려 하면 RECONCILIATION_RUN_NOT_FOUND")
    void finishUnknownRun() {
        assertThatThrownBy(() -> recorder.finish(999L, 1, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(RECONCILIATION_RUN_NOT_FOUND);
    }

    // ===== fail =====

    @Test
    @DisplayName("실행 중인 대사를 FAILED 로 닫고 사유를 남긴다")
    void failRunning() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();

        recorder.fail(runId, "토스 조회 실패");

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.FAILED);
        assertThat(run.getFailureReason()).isEqualTo("토스 조회 실패");
    }

    @Test
    @DisplayName("이미 끝난 대사에는 실패를 기록하지 않는다")
    void failFinishedRunDoesNothing() {
        Long runId = recorder.start(PG, TARGET_DATE).orElseThrow();
        recorder.finish(runId, 5, List.of());

        recorder.fail(runId, "뒤늦은 실패");

        ReconciliationRun run = store.runs.get(0);
        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.MATCHED);
        assertThat(run.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("없는 실행에 실패를 기록하려 하면 RECONCILIATION_RUN_NOT_FOUND")
    void failUnknownRun() {
        assertThatThrownBy(() -> recorder.fail(999L, "실패"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(RECONCILIATION_RUN_NOT_FOUND);
    }
}
