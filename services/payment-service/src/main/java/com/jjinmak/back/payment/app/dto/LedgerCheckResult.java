package com.jjinmak.back.payment.app.dto;

import java.util.List;

/**
 * 원장 대사의 검사 결과. 검사 하나의 결과로도 쓰고, 검사들을 합친 전체 결과로도 쓴다.
 * 검사 단계(읽기 전용 트랜잭션)에서 만들어 기록 단계(트랜잭션)로 넘긴다.
 *
 * - checkedCount : 비교한 대상 수. 검사마다 단위가 다르다 (지갑 수, 거래 그룹 수, 충전 건 수 등)
 * - mismatches   : 발견한 불일치. 없으면 빈 목록
 */
public record LedgerCheckResult(
        int checkedCount,
        List<DetectedMismatchDto> mismatches
) {
}
