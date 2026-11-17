package com.jjinmak.back.payment.domain.enums;

// 대사 실행 상태

/**
 * RUNNING : 대사 실행 중
 * MATCHED : 대사 종료 및 불일치 0건
 * MISMATCHED : 대사 종료 및 불일치 1건 이상
 * FAILED : 대사를 끝내지 못함
 * */
public enum ReconciliationRunStatus {
    RUNNING,
    MATCHED,
    MISMATCHED,
    FAILED
}
