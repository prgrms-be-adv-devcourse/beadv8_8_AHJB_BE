package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.PgMatchResult;
import com.jjinmak.payment.app.dto.PgPaymentState;
import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.enums.ChargeStatus;
import org.springframework.stereotype.Component;

import static com.jjinmak.payment.domain.enums.MismatchType.*;

/**
 * PG 대사의 판정 표. 토스 상태와 충전 건 상태를 대조해 일치 / 불일치 종류를 돌려준다.
 * DB 와 토스를 부르지 않는 순수 로직이다. (조회는 호출하는 쪽이 하고, 여기서는 받은 값만 비교)
 *
 *   토스 ＼ Charge   | 없음    | REQUESTED                 | CHARGED · USED              | CANCELED                    | ABORTED
 *   NONE             | 일치    | 일치                      | INTERNAL_ONLY               | INTERNAL_ONLY               | 일치
 *   APPROVED         | PG_ONLY | PG_APPROVED_NOT_REFLECTED | 일치 (금액 다르면 AMOUNT_MISMATCH) | INTERNAL_CANCELED_NOT_IN_PG | PG_APPROVED_NOT_REFLECTED
 *   CANCELED         | PG_ONLY | 일치                      | PG_CANCELED_NOT_REFLECTED   | 일치                        | 일치
 *   PARTIAL_CANCELED | PG_ONLY | AMOUNT_MISMATCH           | AMOUNT_MISMATCH             | AMOUNT_MISMATCH             | AMOUNT_MISMATCH
 *   UNKNOWN          | 판정 보류 (모든 칸). 토스 단건 조회로 상태를 확정한 뒤 다시 대조한다
 *
 *  - CANCELED × ABORTED = 일치 : 승인 후 DB 반영 실패로 보상 취소한 건, 복구 스케줄러가 토스 취소 후 닫은 건. 정상 경로다
 *  - CANCELED × REQUESTED = 일치 : 보상 취소는 됐고 복구 스케줄러가 아직 ABORTED 로 닫기 전. 돈은 맞다
 *  - APPROVED × ABORTED : 우리는 실패로 닫았는데 토스는 돈을 받았다. 가장 위험한 칸
 *
 * 수동 충전(MANUAL)과 주문 부족분 충전(FOR_PAYMENT)은 구분하지 않는다. 토스 입장에서는 같은 결제다.
 */
@Component
public class PgReconciliationMatcher {

    // 충전 건과 대조한다. 토스에는 거래가 있는데 우리 충전 건이 없으면 charge 에 null 을 넘긴다
    public PgMatchResult match(PgPaymentState toss, Charge charge) {
        if (charge == null) {
            return match(toss, null, 0L);
        }
        return match(toss, charge.getStatus(), charge.getAmount());
    }

    /**
     * 판정에 필요한 값(우리 쪽 상태, 금액)만 받아 대조한다. 충전 건이 없으면 chargeStatus 에 null.
     * Charge 엔티티 대신 값으로 받는 이유: 판정 표를 엔티티 없이 테스트할 수 있고,
     * 나중에 결제 수단이 늘어 Charge 가 아닌 결제 건이 생겨도 같은 표를 쓸 수 있다.
     */
    public PgMatchResult match(PgPaymentState toss, ChargeStatus chargeStatus, long chargeAmount) {
        if (toss.isUnknown()) {
            return PgMatchResult.undetermined();
        }
        if (chargeStatus == null) {
            return matchWithoutCharge(toss);
        }
        // default 를 두지 않는다. ChargeStatus 에 값이 추가되면 컴파일 오류로 이 표를 다시 보게 하기 위함
        return switch (chargeStatus) {
            case REQUESTED, ABORTED -> matchNotCharged(toss);
            case CHARGED, USED -> matchCharged(toss, chargeAmount);
            case CANCELED -> matchCanceled(toss);
        };
    }

    // 충전 건 없음: 토스에 돈이 움직인 거래가 있으면 우리 기록에 없는 결제
    private PgMatchResult matchWithoutCharge(PgPaymentState toss) {
        return switch (toss.kind()) {
            case NONE -> PgMatchResult.matched();
            case APPROVED, CANCELED, PARTIAL_CANCELED -> PgMatchResult.mismatched(PG_ONLY);
            case UNKNOWN -> PgMatchResult.undetermined();
        };
    }

    // REQUESTED / ABORTED: 우리는 돈을 받지 않은 것으로 알고 있음
    private PgMatchResult matchNotCharged(PgPaymentState toss) {
        return switch (toss.kind()) {
            case NONE, CANCELED -> PgMatchResult.matched();
            case APPROVED -> PgMatchResult.mismatched(PG_APPROVED_NOT_REFLECTED);
            case PARTIAL_CANCELED -> PgMatchResult.mismatched(AMOUNT_MISMATCH);
            case UNKNOWN -> PgMatchResult.undetermined();
        };
    }

    // CHARGED / USED: 우리는 돈을 받아 예치금으로 반영했다. 토스에 남은 금액이 충전 금액과 같아야 함
    private PgMatchResult matchCharged(PgPaymentState toss, long chargeAmount) {
        return switch (toss.kind()) {
            case NONE -> PgMatchResult.mismatched(INTERNAL_ONLY);
            case APPROVED -> toss.balanceAmount() == chargeAmount
                    ? PgMatchResult.matched()
                    : PgMatchResult.mismatched(AMOUNT_MISMATCH);
            case CANCELED -> PgMatchResult.mismatched(PG_CANCELED_NOT_REFLECTED);
            case PARTIAL_CANCELED -> PgMatchResult.mismatched(AMOUNT_MISMATCH);
            case UNKNOWN -> PgMatchResult.undetermined();
        };
    }

    // CANCELED: 우리는 예치금을 빼고 토스 취소까지 끝낸 것으로 알고 있음
    private PgMatchResult matchCanceled(PgPaymentState toss) {
        return switch (toss.kind()) {
            case NONE -> PgMatchResult.mismatched(INTERNAL_ONLY);
            case APPROVED -> PgMatchResult.mismatched(INTERNAL_CANCELED_NOT_IN_PG);
            case CANCELED -> PgMatchResult.matched();
            case PARTIAL_CANCELED -> PgMatchResult.mismatched(AMOUNT_MISMATCH);
            case UNKNOWN -> PgMatchResult.undetermined();
        };
    }
}
