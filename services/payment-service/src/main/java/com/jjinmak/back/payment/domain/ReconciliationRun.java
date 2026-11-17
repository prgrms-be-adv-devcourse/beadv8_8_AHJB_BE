package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus;
import com.jjinmak.back.payment.domain.enums.ReconciliationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.*;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.INVALID_RECONCILIATION_STATUS;

/**
 * 대사 실행 1회. "targetDate 의 PG(또는 원장) 대사는 일치했는가" 에 답하는 기록이다.
 *
 * 대사 종류(type)와 대상일(targetDate)마다 1건만 존재한다 (유니크).
 *  - 서버가 여러 대여도 먼저 저장한 쪽만 대사를 진행한다 (중복 실행 차단)
 *  - 같은 날을 다시 돌리면 새 행을 만들지 않고 이 행을 다시 쓴다 (restart)
 *
 *   RUNNING → MATCHED      (끝났고 불일치 0건)
 *   RUNNING → MISMATCHED   (끝났고 불일치 1건 이상)
 *   RUNNING → FAILED       (대사를 끝내지 못함. 불일치가 아니라 "확인 못 함")
 *   MATCHED / MISMATCHED / FAILED → RUNNING   (재실행)
 *
 * - checkedCount  : 비교한 건수. 0 이면 "일치" 가 아니라 "비교할 것이 없었다" 는 뜻
 * - mismatchCount : 확정된 불일치 건수 (= 이 실행에 달린 ReconciliationMismatch 중 이번에 발견된 수)
 * - attempt       : 몇 번째 실행인지. 재실행마다 1 증가
 * - failureReason : FAILED 일 때 대사를 끝내지 못한 이유 (토스 조회 실패 등)
 */
@Entity
@Table(
        name = "reconciliation_run",
        uniqueConstraints = @UniqueConstraint(name = "uk_reconciliation_run_type_date", columnNames = {"type", "target_date"}),
        indexes = @Index(name = "idx_reconciliation_run_status", columnList = "status, target_date")
)
@Getter
@NoArgsConstructor
public class ReconciliationRun extends BaseIdAndTime {

    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReconciliationType type;

    // 대사 대상일. 이 날짜에 일어난 거래를 맞춰 본다 (배치는 전일을 대상으로 실행)
    @Column(nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReconciliationRunStatus status;

    @Column(nullable = false)
    private int checkedCount;

    @Column(nullable = false)
    private int mismatchCount;

    @Column(nullable = false)
    private int attempt;

    // 이번 실행을 시작한 시각. RUNNING 에 오래 머문 건(배치가 중간에 죽은 경우)을 찾는 데 쓴다
    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(length = MAX_FAILURE_REASON_LENGTH)
    private String failureReason;


    private ReconciliationRun(ReconciliationType type, LocalDate targetDate, LocalDateTime startedAt) {
        this.type = type;
        this.targetDate = targetDate;
        this.status = RUNNING;
        this.checkedCount = 0;
        this.mismatchCount = 0;
        this.attempt = 1;
        this.startedAt = startedAt;
    }

    // 대사 시작. 같은 종류·대상일의 실행이 이미 있으면 저장 시 유니크 제약에 걸린다
    public static ReconciliationRun start(ReconciliationType type, LocalDate targetDate, LocalDateTime now) {
        return new ReconciliationRun(type, targetDate, now);
    }

    // 끝난 실행(MATCHED / MISMATCHED / FAILED)을 다시 돌린다. 이전 결과는 지우고 시도 횟수를 올린다
    public void restart(LocalDateTime now) {
        // 이미 실행 중인 대사는 다시 시작할 수 없다
        if (status == RUNNING) {
            throw new BusinessException(INVALID_RECONCILIATION_STATUS);
        }
        // 불일치가 0건인 대사는 재실행하지 않는다
        if (status == MATCHED) {
            throw new BusinessException(INVALID_RECONCILIATION_STATUS);
        }
        this.status = RUNNING;
        this.attempt++;
        this.checkedCount = 0;
        this.mismatchCount = 0;
        this.startedAt = now;
        this.finishedAt = null;
        this.failureReason = null;
    }

    // 대사 종료. 불일치가 없으면 MATCHED, 1건 이상이면 MISMATCHED
    public void finish(LocalDateTime now, int checkedCount, int mismatchCount) {
        requireRunning();
        this.checkedCount = checkedCount;
        this.mismatchCount = mismatchCount;
        this.status = mismatchCount == 0 ? MATCHED : MISMATCHED;
        this.finishedAt = now;
    }

    // 대사를 끝내지 못함 (토스 조회 실패 등). 이미 FAILED 면 아무 것도 하지 않는다 (멱등)
    public void fail(LocalDateTime now, String reason) {
        if (status == FAILED) return;
        requireRunning();
        this.status = FAILED;
        this.finishedAt = now;
        this.failureReason = truncate(reason);
    }

    public boolean isRunning() {
        return status == RUNNING;
    }

    // 끝난 실행인지 (성공·실패 무관)
    public boolean isFinished() {
        return status != RUNNING;
    }

    private void requireRunning() {
        if (status != RUNNING) {
            throw new BusinessException(INVALID_RECONCILIATION_STATUS);   // 실행 중인 대사가 아님
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_FAILURE_REASON_LENGTH) return value;
        return value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
