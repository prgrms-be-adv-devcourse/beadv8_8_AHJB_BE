package com.jjinmak.payment.app.dto;

import com.jjinmak.payment.domain.enums.MismatchType;

/**
 * 토스 상태(PgPaymentState)와 충전 건을 대조한 결과.
 *
 * MATCHED      : 일치
 * MISMATCHED   : 불일치. mismatchType 에 어떤 불일치인지 들어 있다
 * UNDETERMINED : 그날 거래만으로는 판정할 수 없음. 토스 단건 조회로 상태를 확정한 뒤 다시 대조해야 한다
 *
 * mismatchType 은 MISMATCHED 일 때만 값이 있고 나머지는 null 이다.
 */
public record PgMatchResult(
        Verdict verdict,
        MismatchType mismatchType
) {

    public enum Verdict {
        MATCHED,
        MISMATCHED,
        UNDETERMINED
    }

    public static PgMatchResult matched() {
        return new PgMatchResult(Verdict.MATCHED, null);
    }

    public static PgMatchResult mismatched(MismatchType mismatchType) {
        return new PgMatchResult(Verdict.MISMATCHED, mismatchType);
    }

    public static PgMatchResult undetermined() {
        return new PgMatchResult(Verdict.UNDETERMINED, null);
    }

    public boolean isMatched() {
        return verdict == Verdict.MATCHED;
    }

    public boolean isMismatched() {
        return verdict == Verdict.MISMATCHED;
    }

    public boolean isUndetermined() {
        return verdict == Verdict.UNDETERMINED;
    }
}
