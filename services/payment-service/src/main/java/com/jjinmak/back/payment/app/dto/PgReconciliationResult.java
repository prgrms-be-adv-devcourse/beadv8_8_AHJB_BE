package com.jjinmak.back.payment.app.dto;

import java.util.List;

/**
 * PG 대사의 대조 결과. 대조 단계(트랜잭션 없음)에서 만들어 기록 단계(트랜잭션)로 넘긴다.
 *
 * - checkedCount : 비교한 주문번호 수 (토스 거래에 나온 주문번호 + 우리만 승인·취소로 알고 있는 충전 건)
 * - mismatches   : 토스 단건 조회까지 거쳐 확정된 불일치. 없으면 빈 목록
 */
public record PgReconciliationResult(
        int checkedCount,
        List<DetectedMismatchDto> mismatches
) {
}
