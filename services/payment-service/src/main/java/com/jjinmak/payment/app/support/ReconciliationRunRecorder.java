package com.jjinmak.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.domain.ReconciliationMismatch;
import com.jjinmak.payment.domain.ReconciliationRun;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.domain.enums.MismatchType;
import com.jjinmak.payment.domain.enums.ReconciliationType;
import com.jjinmak.payment.out.repository.ReconciliationMismatchRepository;
import com.jjinmak.payment.out.repository.ReconciliationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.jjinmak.payment.exception.PaymentErrorCode.RECONCILIATION_RUN_NOT_FOUND;

/**
 * 대사 실행(ReconciliationRun)의 시작 · 종료 · 실패를 기록하고, 발견한 불일치를 저장한다.
 * PG 대사와 원장 대사가 함께 쓰는 지원 컴포넌트다. 반드시 호출하는 쪽의 트랜잭션 안에서 실행된다.
 *
 * 시작 (start) — 같은 종류 · 대상일의 실행이 이미 있는지에 따라
 *   없음                 → 새로 저장하고 시작
 *   FAILED / MISMATCHED  → 같은 행을 다시 실행 (restart)
 *   MATCHED              → 건너뜀 (불일치 0건으로 끝난 대사는 다시 돌리지 않는다)
 *   RUNNING              → 건너뜀. 단, STALE_AFTER 넘게 멈춰 있으면 중간에 죽은 실행으로 보고 다시 시작
 *
 * 종료 (finish) — 이번에 발견한 불일치를 이 실행에 이미 있던 불일치와 비교해서
 *   처음 발견              → 새로 저장
 *   이전에도 있었고 OPEN     → 그대로 둠
 *   이전에 RESOLVED 로 닫음  → 실제로는 해결되지 않았으므로 다시 OPEN (reopen)
 *   이전에 IGNORED 로 닫음   → 그대로 둠. 불일치 건수에도 넣지 않는다 (운영자가 조치 불필요로 판단한 건)
 *   이전에 OPEN 인데 이번엔 없음 → SYSTEM 이 RESOLVED 로 닫음
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReconciliationRunRecorder {

    // RUNNING 에 이 시간 넘게 머문 실행은 중간에 죽은 것으로 본다
    static final Duration STALE_AFTER = Duration.ofHours(1);

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationMismatchRepository mismatchRepository;

    /**
     * 대사를 시작한다.
     * 반환 : 진행할 실행의 id. 이미 끝났거나 다른 곳에서 실행 중이라 진행하지 않으면 빈 값
     *
     * 실행이 없어 새로 저장하는데 다른 서버가 같은 순간에 먼저 저장했다면 유니크 제약(type, target_date) 위반으로
     * DataIntegrityViolationException 이 난다. 호출하는 쪽이 "다른 서버가 실행 중" 으로 보고 건너뛰면 된다.
     */
    public Optional<Long> start(ReconciliationType type, LocalDate targetDate) {
        LocalDateTime now = LocalDateTime.now();

        Optional<ReconciliationRun> existing = runRepository.findByTypeAndTargetDate(type, targetDate);
        if (existing.isEmpty()) {
            ReconciliationRun created = runRepository.saveAndFlush(ReconciliationRun.start(type, targetDate, now));
            log.info("[대사] 시작. runId={}, type={}, targetDate={}", created.getId(), type, targetDate);
            return Optional.of(created.getId());
        }

        ReconciliationRun run = existing.get();
        return switch (run.getStatus()) {
            case MATCHED -> {
                log.info("[대사] 이미 일치로 끝난 대사라 건너뜀. runId={}, type={}, targetDate={}", run.getId(), type, targetDate);
                yield Optional.empty();
            }
            case RUNNING -> restartIfStale(run, now);
            case MISMATCHED, FAILED -> {
                run.restart(now);
                log.info("[대사] 재실행. runId={}, type={}, targetDate={}, attempt={}",
                        run.getId(), type, targetDate, run.getAttempt());
                yield Optional.of(run.getId());
            }
        };
    }

    /**
     * 대사를 끝낸다. 발견한 불일치를 저장하고 실행을 MATCHED / MISMATCHED 로 닫는다.
     * checkedCount : 비교한 건수
     * detected : 이번 실행에서 발견한 불일치 (없으면 빈 목록)
     */
    public void finish(Long runId, int checkedCount, List<DetectedMismatchDto> detected) {
        LocalDateTime now = LocalDateTime.now();
        ReconciliationRun run = getRun(runId);

        Map<MismatchKey, ReconciliationMismatch> previousByKey = new HashMap<>();
        for (ReconciliationMismatch previous : mismatchRepository.findByRun(run)) {
            previousByKey.put(MismatchKey.of(previous), previous);
        }

        Set<MismatchKey> detectedKeys = new HashSet<>();
        int created = 0;
        int kept = 0;
        int reopened = 0;

        for (DetectedMismatchDto dto : detected) {
            MismatchKey key = MismatchKey.of(dto);
            if (!detectedKeys.add(key)) {
                continue;   // 같은 불일치가 두 번 넘어오면 한 번만 처리
            }
            ReconciliationMismatch previous = previousByKey.get(key);
            if (previous == null) {
                mismatchRepository.save(dto.toEntity(run));
                created++;
                continue;
            }
            switch (previous.getStatus()) {
                case OPEN -> kept++;
                case RESOLVED -> {
                    previous.reopen();
                    reopened++;
                }
                case IGNORED -> {
                    // 운영자가 조치 불필요로 닫은 건. 다시 발견돼도 그대로 두고 건수에 넣지 않는다
                }
            }
        }

        // 이전 실행에서는 있었는데 이번에는 발견되지 않은 OPEN 건은 해결된 것으로 닫는다
        int autoResolved = 0;
        for (Map.Entry<MismatchKey, ReconciliationMismatch> entry : previousByKey.entrySet()) {
            if (!detectedKeys.contains(entry.getKey()) && entry.getValue().isOpen()) {
                entry.getValue().resolveBySystem(now);
                autoResolved++;
            }
        }

        int mismatchCount = created + kept + reopened;
        run.finish(now, checkedCount, mismatchCount);

        if (mismatchCount > 0) {
            log.error("[대사] 불일치 {}건 발견. runId={}, type={}, targetDate={}, 비교 {}건 (신규 {}, 유지 {}, 재오픈 {}, 자동 해결 {})",
                    mismatchCount, run.getId(), run.getType(), run.getTargetDate(), checkedCount,
                    created, kept, reopened, autoResolved);
        } else {
            log.info("[대사] 일치. runId={}, type={}, targetDate={}, 비교 {}건 (자동 해결 {})",
                    run.getId(), run.getType(), run.getTargetDate(), checkedCount, autoResolved);
        }
    }


    // 대사를 끝내지 못했을 때 (토스 조회 실패 등) 실행을 FAILED 로 닫는다.
    // 대사 도중 예외로 롤백된 뒤에 별도 트랜잭션에서 부른다. 이미 끝난 실행이면 아무 것도 하지 않는다.
    public void fail(Long runId, String reason) {
        ReconciliationRun run = getRun(runId);
        if (!run.isRunning()) {
            return;
        }
        run.fail(LocalDateTime.now(), reason);
        log.error("[대사] 실행 실패. runId={}, type={}, targetDate={}, reason={}",
                run.getId(), run.getType(), run.getTargetDate(), reason);
    }

    // 실행 중인 대사: 시작한 지 얼마 안 됐으면 다른 곳에서 돌고 있는 것이므로 건너뛰고, 오래 멈춰 있으면 다시 시작한다
    private Optional<Long> restartIfStale(ReconciliationRun run, LocalDateTime now) {
        if (run.getStartedAt().isAfter(now.minus(STALE_AFTER))) {
            log.info("[대사] 이미 실행 중이라 건너뜀. runId={}, type={}, targetDate={}, startedAt={}",
                    run.getId(), run.getType(), run.getTargetDate(), run.getStartedAt());
            return Optional.empty();
        }
        log.warn("[대사] 실행 중 상태로 멈춰 있던 대사를 다시 시작. runId={}, type={}, targetDate={}, startedAt={}",
                run.getId(), run.getType(), run.getTargetDate(), run.getStartedAt());
        run.fail(now, "이전 실행이 끝나지 않은 채 멈춰 있어 실패 처리");
        run.restart(now);
        return Optional.of(run.getId());
    }

    private ReconciliationRun getRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(RECONCILIATION_RUN_NOT_FOUND));
    }

    // 같은 실행 안에서 불일치 1건을 가리키는 키. ReconciliationMismatch 의 유니크 제약(run, type, targetType, targetKey)과 같다
    private record MismatchKey(MismatchType type, MismatchTargetType targetType, String targetKey) {

        static MismatchKey of(ReconciliationMismatch mismatch) {
            return new MismatchKey(mismatch.getType(), mismatch.getTargetType(), mismatch.getTargetKey());
        }

        static MismatchKey of(DetectedMismatchDto dto) {
            return new MismatchKey(dto.type(), dto.targetType(), dto.targetKey());
        }
    }
}
