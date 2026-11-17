package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.app.dto.PgMatchResult;
import com.jjinmak.back.payment.app.dto.PgPaymentState;
import com.jjinmak.back.payment.app.dto.PgPaymentState.Kind;
import com.jjinmak.back.payment.app.support.PgReconciliationMatcher;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.MismatchType;
import com.jjinmak.back.payment.support.ChargeFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.jjinmak.back.payment.domain.enums.ChargeStatus.ABORTED;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.payment.domain.enums.ChargeStatus.USED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.AMOUNT_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.INTERNAL_CANCELED_NOT_IN_PG;
import static com.jjinmak.back.payment.domain.enums.MismatchType.INTERNAL_ONLY;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_APPROVED_NOT_REFLECTED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_CANCELED_NOT_REFLECTED;
import static com.jjinmak.back.payment.domain.enums.MismatchType.PG_ONLY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PG 대사 판정 표. 토스 상태와 충전 건 상태의 조합마다 일치 / 불일치 종류가 맞게 나오는지 본다.
 */
class PgReconciliationMatcherTest {

    private static final long AMOUNT = 20_000L;

    private final PgReconciliationMatcher matcher = new PgReconciliationMatcher();

    // 충전 금액이 AMOUNT 인 결제의 토스 쪽 상태
    private static PgPaymentState toss(Kind kind) {
        return switch (kind) {
            case NONE -> PgPaymentState.none("CHG-test");
            case APPROVED -> new PgPaymentState("CHG-test", "pk-test", kind, AMOUNT, AMOUNT);
            case CANCELED -> new PgPaymentState("CHG-test", "pk-test", kind, AMOUNT, 0L);
            case PARTIAL_CANCELED -> new PgPaymentState("CHG-test", "pk-test", kind, AMOUNT, 5_000L);
            case UNKNOWN -> new PgPaymentState("CHG-test", "pk-test", kind, 0L, 0L);
        };
    }

    @ParameterizedTest(name = "토스 {0} × 충전 건 {1} → {2}")
    @MethodSource("table")
    @DisplayName("판정 표: 토스 상태 × 충전 건 상태 (기대값 null = 일치)")
    void judgmentTable(Kind tossKind, ChargeStatus chargeStatus, MismatchType expected) {
        PgMatchResult result = matcher.match(toss(tossKind), chargeStatus, AMOUNT);

        if (expected == null) {
            assertThat(result.isMatched()).isTrue();
            assertThat(result.mismatchType()).isNull();
        } else {
            assertThat(result.isMismatched()).isTrue();
            assertThat(result.mismatchType()).isEqualTo(expected);
        }
    }

    // 충전 건 상태가 null 이면 "충전 건 없음", 기대값이 null 이면 "일치"
    static Stream<Arguments> table() {
        ChargeStatus canceled = ChargeStatus.CANCELED;
        return Stream.of(
                // 토스에 거래 없음
                Arguments.of(Kind.NONE, null, null),
                Arguments.of(Kind.NONE, REQUESTED, null),
                Arguments.of(Kind.NONE, CHARGED, INTERNAL_ONLY),
                Arguments.of(Kind.NONE, USED, INTERNAL_ONLY),
                Arguments.of(Kind.NONE, canceled, INTERNAL_ONLY),
                Arguments.of(Kind.NONE, ABORTED, null),
                // 토스 승인
                Arguments.of(Kind.APPROVED, null, PG_ONLY),
                Arguments.of(Kind.APPROVED, REQUESTED, PG_APPROVED_NOT_REFLECTED),
                Arguments.of(Kind.APPROVED, CHARGED, null),
                Arguments.of(Kind.APPROVED, USED, null),
                Arguments.of(Kind.APPROVED, canceled, INTERNAL_CANCELED_NOT_IN_PG),
                Arguments.of(Kind.APPROVED, ABORTED, PG_APPROVED_NOT_REFLECTED),
                // 토스 전액 취소
                Arguments.of(Kind.CANCELED, null, PG_ONLY),
                Arguments.of(Kind.CANCELED, REQUESTED, null),
                Arguments.of(Kind.CANCELED, CHARGED, PG_CANCELED_NOT_REFLECTED),
                Arguments.of(Kind.CANCELED, USED, PG_CANCELED_NOT_REFLECTED),
                Arguments.of(Kind.CANCELED, canceled, null),
                Arguments.of(Kind.CANCELED, ABORTED, null),
                // 토스 부분 취소
                Arguments.of(Kind.PARTIAL_CANCELED, null, PG_ONLY),
                Arguments.of(Kind.PARTIAL_CANCELED, REQUESTED, AMOUNT_MISMATCH),
                Arguments.of(Kind.PARTIAL_CANCELED, CHARGED, AMOUNT_MISMATCH),
                Arguments.of(Kind.PARTIAL_CANCELED, USED, AMOUNT_MISMATCH),
                Arguments.of(Kind.PARTIAL_CANCELED, canceled, AMOUNT_MISMATCH),
                Arguments.of(Kind.PARTIAL_CANCELED, ABORTED, AMOUNT_MISMATCH)
        );
    }

    @ParameterizedTest(name = "토스 UNKNOWN × 충전 건 {0} → 판정 보류")
    @MethodSource("everyChargeStatus")
    @DisplayName("토스 상태를 알 수 없으면 충전 건이 무엇이든 판정을 보류한다")
    void unknownIsUndetermined(ChargeStatus chargeStatus) {
        PgMatchResult result = matcher.match(toss(Kind.UNKNOWN), chargeStatus, AMOUNT);

        assertThat(result.isUndetermined()).isTrue();
        assertThat(result.isMatched()).isFalse();
        assertThat(result.isMismatched()).isFalse();
        assertThat(result.mismatchType()).isNull();
    }

    static Stream<ChargeStatus> everyChargeStatus() {
        return Stream.of(ChargeStatus.values());
    }

    @Test
    @DisplayName("충전 건이 없고 토스 상태도 알 수 없으면 판정을 보류한다")
    void unknownWithoutCharge() {
        assertThat(matcher.match(toss(Kind.UNKNOWN), null, 0L).isUndetermined()).isTrue();
    }

    @Test
    @DisplayName("토스는 승인인데 남은 금액이 충전 금액과 다르면 AMOUNT_MISMATCH")
    void approvedButAmountDiffers() {
        PgPaymentState approved30000 = new PgPaymentState("CHG-test", "pk-test", Kind.APPROVED, 30_000L, 30_000L);

        assertThat(matcher.match(approved30000, CHARGED, AMOUNT).mismatchType()).isEqualTo(AMOUNT_MISMATCH);
        assertThat(matcher.match(approved30000, USED, AMOUNT).mismatchType()).isEqualTo(AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("충전 건 엔티티를 넘기면 그 상태와 금액으로 판정한다")
    void matchWithChargeEntity() {
        Charge charged = ChargeFixture.charge(1L, "CHG-test", CHARGED, AMOUNT);

        assertThat(matcher.match(toss(Kind.APPROVED), charged).isMatched()).isTrue();
        assertThat(matcher.match(toss(Kind.CANCELED), charged).mismatchType()).isEqualTo(PG_CANCELED_NOT_REFLECTED);
    }

    @Test
    @DisplayName("충전 건 엔티티가 null 이면 충전 건 없음으로 판정한다")
    void matchWithoutChargeEntity() {
        Charge none = null;

        assertThat(matcher.match(toss(Kind.APPROVED), none).mismatchType()).isEqualTo(PG_ONLY);
        assertThat(matcher.match(toss(Kind.NONE), none).isMatched()).isTrue();
    }
}
