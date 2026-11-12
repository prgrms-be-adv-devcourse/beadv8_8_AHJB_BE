package com.jjinmak.payment.domain.enums;

//대사 종류
public enum ReconciliationType{
    PG, // 토스 거래 내역 <> Charge
    LEDGER // 원장 <> 잔액/홀딩/충전 상태
}
