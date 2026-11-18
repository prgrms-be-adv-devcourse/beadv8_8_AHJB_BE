package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import com.jjinmak.back.payment.domain.enums.MismatchStatus;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.domain.enums.MismatchType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.jjinmak.back.payment.domain.enums.MismatchStatus.*;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.*;

/**
 * 대사에서 발견한 불일치 1건. 운영자가 보고 처리하는 단위다. (일치한 건은 저장하지 않는다)
 *
 *   OPEN → RESOLVED   (돈이나 상태를 맞춰서 해결)
 *   OPEN → IGNORED    (확인했고 조치가 필요 없음)
 *   RESOLVED → OPEN   (해결했다고 닫았는데 재실행에서 다시 발견됨)
 *
 * 무엇이 어긋났는지
 * - type       : 어떻게 어긋났는지 (MismatchType)
 * - targetType : 어긋난 대상의 종류
 * - targetKey  : 그 대상의 식별자. targetType 에 따라 chargeId / pgOrderId / walletId / orderId / transactionGroupId
 * - pgOrderId  : PG 대사일 때 토스 주문번호. 토스 개발자센터에서 바로 찾을 수 있게 따로 둔다. 원장 대사는 null
 *
 * 얼마나 어긋났는지 — expected 는 기준 값, actual 은 우리가 반영한 값
 * - PG 대사   : expected = 토스 (상태, 토스에 남아 있는 금액) / actual = Charge (상태, 예치금으로 인정한 금액)
 * - 원장 대사 : expected = 원장 (기록이 말하는 상태, 합계)    / actual = 잔액·홀딩·충전 상태
 * 상태나 금액 중 비교하지 않는 쪽은 null 로 둔다.
 *
 * 같은 실행에서 같은 대상의 같은 불일치는 1건만 저장된다 (유니크). 재실행해도 중복되지 않는다.
 */
@Entity
@Table(
        name = "reconciliation_mismatch",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reconciliation_mismatch_target",
                columnNames = {"run_id", "type", "target_type", "target_key"}
        ),
        indexes = {
                @Index(name = "idx_reconciliation_mismatch_status", columnList = "status, created_at"),
                @Index(name = "idx_reconciliation_mismatch_pg_order", columnList = "pg_order_id")
        }
)
@Getter
@NoArgsConstructor
public class ReconciliationMismatch extends BaseIdAndTime {

    // 재실행에서 더 이상 발견되지 않아 자동으로 닫을 때의 처리자
    public static final String RESOLVED_BY_SYSTEM = "SYSTEM";

    private static final int MAX_TEXT_LENGTH = 500;

    // 재오픈 이력에 남길 처리 사유의 최대 길이. detail(500자)에 원래 설명과 이력이 함께 들어가도록 사유는 앞부분만 남긴다
    private static final int MAX_HISTORY_NOTE_LENGTH = 100;
    private static final String HISTORY_SEPARATOR = " | ";
    private static final String ELLIPSIS = "…";
    private static final DateTimeFormatter HISTORY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 어느 대사 실행에서 발견됐는지. 대상일과 대사 종류는 여기서 따라간다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private MismatchType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MismatchTargetType targetType;

    @Column(nullable = false, length = 64)
    private String targetKey;

    @Column(length = 64)
    private String pgOrderId;

    @Column(length = 30)
    private String expectedState;

    @Column(length = 30)
    private String actualState;

    private Long expectedAmount;

    private Long actualAmount;

    // 사람이 읽을 설명. 위 값들로 표현되지 않는 맥락을 적는다. 재오픈되면 이전 처리 기록이 뒤에 덧붙는다
    @Column(length = MAX_TEXT_LENGTH)
    private String detail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MismatchStatus status;

    private LocalDateTime resolvedAt;

    // 처리한 주체. 운영자 id 또는 SYSTEM
    @Column(length = 50)
    private String resolvedBy;

    // 어떻게 처리했는지. 돈이 걸린 기록이라 처리할 때 반드시 남긴다
    @Column(length = MAX_TEXT_LENGTH)
    private String resolutionNote;


    private ReconciliationMismatch(ReconciliationRun run, MismatchType type,
                                   MismatchTargetType targetType, String targetKey, String pgOrderId,
                                   String expectedState, Long expectedAmount,
                                   String actualState, Long actualAmount, String detail) {
        if (!type.belongsTo(run.getType())) {
            throw new BusinessException(MISMATCH_TYPE_NOT_ALLOWED);
        }
        if (targetKey == null || targetKey.isBlank()) {
            throw new BusinessException(MISMATCH_TARGET_KEY_REQUIRED);
        }
        this.run = run;
        this.type = type;
        this.targetType = targetType;
        this.targetKey = targetKey;
        this.pgOrderId = pgOrderId;
        this.expectedState = expectedState;
        this.expectedAmount = expectedAmount;
        this.actualState = actualState;
        this.actualAmount = actualAmount;
        this.detail = truncate(detail);
        this.status = OPEN;
    }

    // PG 대사에서 발견한 불일치. expected = 토스, actual = Charge
    public static ReconciliationMismatch ofPg(ReconciliationRun run, MismatchType type,
                                              MismatchTargetType targetType, String targetKey, String pgOrderId,
                                              String expectedState, Long expectedAmount,
                                              String actualState, Long actualAmount, String detail) {
        return new ReconciliationMismatch(run, type, targetType, targetKey, pgOrderId,
                expectedState, expectedAmount, actualState, actualAmount, detail);
    }

    // 원장 대사에서 발견한 불일치. expected = 원장, actual = 잔액·홀딩·충전 상태
    public static ReconciliationMismatch ofLedger(ReconciliationRun run, MismatchType type,
                                                  MismatchTargetType targetType, String targetKey,
                                                  String expectedState, Long expectedAmount,
                                                  String actualState, Long actualAmount, String detail) {
        return new ReconciliationMismatch(run, type, targetType, targetKey, null,
                expectedState, expectedAmount, actualState, actualAmount, detail);
    }

    // 운영자가 돈이나 상태를 맞춰서 해결. 이미 RESOLVED 면 아무 것도 하지 않는다 (멱등)
    public void resolve(LocalDateTime now, String resolvedBy, String note) {
        if (status == RESOLVED) return;
        close(RESOLVED, now, resolvedBy, note);
    }

    // 확인했고 조치가 필요 없음. 이미 IGNORED 면 아무 것도 하지 않는다 (멱등)
    public void ignore(LocalDateTime now, String resolvedBy, String note) {
        if (status == IGNORED) return;
        close(IGNORED, now, resolvedBy, note);
    }

    // 재실행에서 더 이상 발견되지 않은 건을 자동으로 닫는다. 이미 처리된 건은 건드리지 않는다
    public void resolveBySystem(LocalDateTime now) {
        if (status != OPEN) return;
        close(RESOLVED, now, RESOLVED_BY_SYSTEM, "재실행에서 더 이상 발견되지 않음");
    }

    // 해결했다고 닫은 건(RESOLVED)이 재실행에서 다시 발견됨. 실제로는 해결되지 않았으므로 OPEN 으로 되돌린다.
    // 처리 기록(처리자·처리 시각·사유)은 비우기 전에 detail 뒤에 이력으로 덧붙여, 누가 언제 어떤 사유로 닫았었는지 남긴다.
    // 이미 OPEN 이면 아무 것도 하지 않는다 (멱등). IGNORED 는 운영자가 조치 불필요로 판단한 건이라 되돌리지 않는다
    public void reopen() {
        if (status == OPEN) return;
        if (status != RESOLVED) {
            throw new BusinessException(INVALID_MISMATCH_STATUS);
        }
        this.detail = appendHistory(this.detail, previousResolution());
        this.status = OPEN;
        this.resolvedAt = null;
        this.resolvedBy = null;
        this.resolutionNote = null;
    }

    public boolean isOpen() {
        return status == OPEN;
    }

    // 어긋난 금액 (기준 값 - 우리 값). 금액을 비교하지 않는 불일치면 null
    public Long getDiffAmount() {
        if (expectedAmount == null || actualAmount == null) return null;
        return expectedAmount - actualAmount;
    }

    private void close(MismatchStatus next, LocalDateTime now, String resolvedBy, String note) {
        if (status != OPEN) {
            throw new BusinessException(INVALID_MISMATCH_STATUS);
        }
        if (resolvedBy == null || resolvedBy.isBlank()) {
            throw new BusinessException(MISMATCH_RESOLVER_REQUIRED);
        }
        if (note == null || note.isBlank()) {
            throw new BusinessException(MISMATCH_RESOLUTION_NOTE_REQUIRED);
        }
        this.status = next;
        this.resolvedAt = now;
        this.resolvedBy = resolvedBy;
        this.resolutionNote = truncate(note);
    }

    // 비우기 전의 처리 기록 한 줄. 예: 이전 처리: 7, 2026-11-12 10:00, 토스에서 취소 후 확인
    private String previousResolution() {
        String at = resolvedAt == null ? "-" : resolvedAt.format(HISTORY_TIME_FORMAT);
        return "이전 처리: " + resolvedBy + ", " + at + ", " + shorten(resolutionNote, MAX_HISTORY_NOTE_LENGTH);
    }

    // detail 뒤에 이력을 덧붙인다. 500자를 넘으면 기존 내용의 뒷부분을 줄여서, 방금 덧붙인 이력이 잘리지 않게 한다
    private static String appendHistory(String detail, String history) {
        if (detail == null || detail.isBlank()) return truncate(history);
        String suffix = HISTORY_SEPARATOR + history;
        int room = MAX_TEXT_LENGTH - suffix.length();
        if (room <= 0) return truncate(history);
        return shorten(detail, room) + suffix;
    }

    // max 자를 넘으면 잘라내고 끝에 … 를 붙인다 (결과는 max 자 이하)
    private static String shorten(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, max - 1) + ELLIPSIS;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) return value;
        return value.substring(0, MAX_TEXT_LENGTH);
    }
}
