package com.jjinmak.payment.app.dto;

import com.jjinmak.payment.domain.ReconciliationMismatch;
import com.jjinmak.payment.domain.ReconciliationRun;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.domain.enums.MismatchType;
import com.jjinmak.payment.domain.enums.ReconciliationType;

/**
 * 이번 대사 실행에서 발견한 불일치 1건.
 *
 * 대조 단계(토스 호출이 끼어 트랜잭션 없이 실행)의 결과를 기록 단계(트랜잭션)로 넘기기 위한 값이다.
 * 저장은 ReconciliationRunRecorder 가 하며, 필드의 의미는 ReconciliationMismatch 와 같다.
 *
 * - pgOrderId                      : PG 대사일 때만. 원장 대사는 null
 * - expectedState / expectedAmount : 기준 쪽 값 (PG 대사 = 토스, 원장 대사 = 원장)
 * - actualState / actualAmount     : 우리가 반영한 값
 */
public record DetectedMismatchDto(
        MismatchType type,
        MismatchTargetType targetType,
        String targetKey,
        String pgOrderId,
        String expectedState,
        Long expectedAmount,
        String actualState,
        Long actualAmount,
        String detail
) {

    // 이 실행(run)에 달린 불일치 엔티티로 바꾼다. PG 대사의 불일치인지 원장 대사의 불일치인지는 종류(type)로 구분한다
    public ReconciliationMismatch toEntity(ReconciliationRun run) {
        if (type.belongsTo(ReconciliationType.PG)) {
            return ReconciliationMismatch.ofPg(run, type, targetType, targetKey, pgOrderId,
                    expectedState, expectedAmount, actualState, actualAmount, detail);
        }
        return ReconciliationMismatch.ofLedger(run, type, targetType, targetKey,
                expectedState, expectedAmount, actualState, actualAmount, detail);
    }
}
