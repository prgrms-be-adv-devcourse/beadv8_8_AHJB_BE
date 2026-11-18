package com.jjinmak.back.boundedContext.payment.domain.enums;

/**
 * 출금 상태. 지금은 실제 송금이 없어 요청 즉시 COMPLETED 로 기록된다.
 * 계좌 송금이 연동되면 REQUESTED(송금 대기) → COMPLETED / FAILED 로 바뀐다.
 */
public enum WithdrawalStatus {
    REQUESTED,
    COMPLETED,
    FAILED
}
