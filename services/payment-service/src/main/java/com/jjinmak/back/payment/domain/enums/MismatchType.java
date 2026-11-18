package com.jjinmak.back.payment.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.jjinmak.back.payment.domain.enums.ReconciliationType.LEDGER;
import static com.jjinmak.back.payment.domain.enums.ReconciliationType.PG;

/**
 *  - PG_ONLY                     : 토스에 거래가 있는데 우리 Charge 가 없음
 *  - INTERNAL_ONLY               : 우리는 승인으로 아는데 토스에 거래가 없음
 *  - PG_APPROVED_NOT_REFLECTED   : 토스는 승인, Charge 는 REQUESTED / ABORTED (돈은 받았는데 예치금이 없음)
 *  - PG_CANCELED_NOT_REFLECTED   : 토스는 취소, Charge 는 CHARGED / USED (환불됐는데 예치금이 살아 있음)
 *  - INTERNAL_CANCELED_NOT_IN_PG : Charge 는 CANCELED, 토스는 승인 그대로 (예치금은 뺐는데 환불이 안 됨)
 *  - AMOUNT_MISMATCH             : 금액이 다름 (부분 취소 포함)
 *
 * 원장 대사 (원장 <> 잔액 / 홀딩 / 충전 상태) — 기준은 원장
 *  - LEDGER_GROUP_UNBALANCED     : 거래 그룹(transactionGroupId)의 금액 합이 0 이 아님
 *  - BALANCE_LEDGER_MISMATCH     : 지갑 잔액 != 원장 합계
 *  - ESCROW_HOLDING_MISMATCH     : ESCROW 잔액 != 미정산(HELD + CONFIRMED) 홀딩 합
 *  - DEPOSIT_REMAINING_MISMATCH  : 예치금 < 충전 잔여금(remainingAmount) 합
 *  - CHARGE_LEDGER_MISMATCH      : Charge 상태와 원장의 CHARGE / CHARGE_CANCEL 기록이 안 맞음
 *  - HOLDING_LEDGER_MISMATCH     : Holding 상태와 원장의 HOLD / REFUND / SETTLEMENT 기록이 안 맞음
 */
@Getter
@RequiredArgsConstructor
public enum MismatchType {

    // ===== PG 대사 =====
    PG_ONLY(PG, "토스에 거래가 있는데 우리 충전 건이 없음"),
    INTERNAL_ONLY(PG, "우리는 승인으로 아는데 토스에 거래가 없음"),
    PG_APPROVED_NOT_REFLECTED(PG, "토스는 승인 상태인데 충전 건에 반영되지 않음"),
    PG_CANCELED_NOT_REFLECTED(PG, "토스는 취소됐는데 충전 건에 반영되지 않음"),
    INTERNAL_CANCELED_NOT_IN_PG(PG, "충전 건은 취소됐는데 토스는 승인 상태 그대로임"),
    AMOUNT_MISMATCH(PG, "토스 금액과 충전 금액이 다름"),

    // ===== 원장 대사 =====
    LEDGER_GROUP_UNBALANCED(LEDGER, "거래 그룹의 금액 합이 0 이 아님"),
    BALANCE_LEDGER_MISMATCH(LEDGER, "지갑 잔액과 원장 합계가 다름"),
    ESCROW_HOLDING_MISMATCH(LEDGER, "ESCROW 잔액과 미정산 홀딩 합이 다름"),
    DEPOSIT_REMAINING_MISMATCH(LEDGER, "예치금이 충전 잔여금 합보다 적음"),
    CHARGE_LEDGER_MISMATCH(LEDGER, "충전 건 상태와 원장 기록이 안 맞음"),
    HOLDING_LEDGER_MISMATCH(LEDGER, "홀딩 상태와 원장 기록이 안 맞음");

    private final ReconciliationType reconciliationType;
    private final String description;

    // 이 불일치가 해당 대사에서 나올 수 있는 종류인지
    public boolean belongsTo(ReconciliationType type) {
        return this.reconciliationType == type;
    }
}
