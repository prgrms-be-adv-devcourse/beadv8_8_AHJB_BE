package com.jjinmak.back.payment.domain.enums;

/**
 * OPEN : 불일치 건 발견
 * RESOLVED : 불일치 건 해결
 * IGNORED : 불일치 건 확인 및 조치 필요 없음*/
public enum MismatchStatus {
    OPEN,
    RESOLVED,
    IGNORED
}
