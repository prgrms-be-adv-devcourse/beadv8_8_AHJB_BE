package com.jjinmak.payment.in.scheduler;

import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.app.dto.PgReconciliationResult;
import com.jjinmak.payment.app.facade.ReconciliationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 대사 스케줄러.
 *
 * 매일 새벽 4시에 전날 하루치 PG 대사를, 4시 30분에 원장 대사를 돌린다.

 * 한 번의 대사는 시작 -> 대조 -> 종료 순서이고 단계마다 트랜잭션이 따로다.
 * 대조나 종료에서 예외가 나면 그 단계는 롤백되므로, 실패 기록은 별도 트랜잭션(failPg / failLedger)으로 남긴다.
 *
 * 서버가 여러 대여도 대사는 한 곳에서만 진행된다. 실행 기록의 유니크 제약(종류, 대상일) 때문에
 * 먼저 저장한 서버만 시작에 성공하고, 나머지는 DataIntegrityViolationException 을 받아 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private static final String ZONE = "Asia/Seoul";

    private final ReconciliationFacade reconciliationFacade;

    @Scheduled(cron = "0 0 4 * * *", zone = ZONE)
    public void reconcileYesterdayPg() {
        reconcilePg(LocalDate.now(ZoneId.of(ZONE)).minusDays(1));
    }

    // 대상일 하루치 PG 대사 1회. 날짜를 받으므로 특정 날짜를 다시 돌릴 때도 쓸 수 있다
    public void reconcilePg(LocalDate targetDate) {
        Optional<Long> runId;
        try {
            runId = reconciliationFacade.startPg(targetDate);
        } catch (DataIntegrityViolationException e) {
            log.info("[PG 대사] 다른 서버가 먼저 시작해 건너뜀. targetDate={}", targetDate);
            return;
        }
        if (runId.isEmpty()) {
            return;   // 이미 일치로 끝났거나 다른 곳에서 실행 중
        }

        try {
            PgReconciliationResult result = reconciliationFacade.reconcilePg(targetDate);
            reconciliationFacade.finishPg(runId.get(), result);
        } catch (Exception e) {
            log.error("[PG 대사] 실패. runId={}, targetDate={}", runId.get(), targetDate, e);
            recordPgFailure(runId.get(), e);
        }
    }

    @Scheduled(cron = "0 30 4 * * *", zone = ZONE)
    public void reconcileYesterdayLedger() {
        reconcileLedger(LocalDate.now(ZoneId.of(ZONE)).minusDays(1));
    }

    // 대상일의 원장 대사 1회. 날짜를 받으므로 특정 날짜를 다시 돌릴 때도 쓸 수 있다
    public void reconcileLedger(LocalDate targetDate) {
        Optional<Long> runId;
        try {
            runId = reconciliationFacade.startLedger(targetDate);
        } catch (DataIntegrityViolationException e) {
            log.info("[원장 대사] 다른 서버가 먼저 시작해 건너뜀. targetDate={}", targetDate);
            return;
        }
        if (runId.isEmpty()) {
            return;   // 이미 일치로 끝났거나 다른 곳에서 실행 중
        }

        try {
            LedgerCheckResult result = reconciliationFacade.checkLedger(targetDate);
            reconciliationFacade.finishLedger(runId.get(), result);
        } catch (Exception e) {
            log.error("[원장 대사] 실패. runId={}, targetDate={}", runId.get(), targetDate, e);
            recordLedgerFailure(runId.get(), e);
        }
    }

    private void recordPgFailure(Long runId, Exception cause) {
        try {
            reconciliationFacade.failPg(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log.error("[PG 대사] 실패 기록도 실패. runId={}", runId, e);
        }
    }

    private void recordLedgerFailure(Long runId, Exception cause) {
        try {
            reconciliationFacade.failLedger(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log.error("[원장 대사] 실패 기록도 실패. runId={}", runId, e);
        }
    }
}
