package com.jjinmak.payment.domain.enums;

/**
 * 토스 웹훅 이벤트 처리 상태.
 *
 * RECEIVED  : 수신해서 저장만 한 상태. 아직 처리 전
 * PROCESSED : 토스 조회 결과를 결제·충전에 반영 완료
 * IGNORED   : 이미 같은 상태이거나 처리 대상이 아니라서 반영할 것이 없음 (중복 수신 포함)
 * FAILED    : 처리 중 실패. 재시도 대상
 *
 * PROCESSED, IGNORED 는 끝난 상태라 다시 바뀌지 않는다.
 */
public enum WebhookEventStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED;

    public boolean isDone() {
        return this == PROCESSED || this == IGNORED;
    }
}
