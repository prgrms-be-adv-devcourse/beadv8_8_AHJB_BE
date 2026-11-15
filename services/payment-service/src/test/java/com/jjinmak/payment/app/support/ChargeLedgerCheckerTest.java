package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.domain.enums.ChargeStatus;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.out.repository.ChargeRepository;
import com.jjinmak.payment.support.ChargeFixture;
import com.jjinmak.payment.support.LedgerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.jjinmak.payment.domain.enums.ChargeStatus.ABORTED;
import static com.jjinmak.payment.domain.enums.ChargeStatus.CANCELED;
import static com.jjinmak.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.payment.domain.enums.ChargeStatus.USED;
import static com.jjinmak.payment.domain.enums.MismatchType.CHARGE_LEDGER_MISMATCH;
import static com.jjinmak.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.CHARGE_CANCEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 원장 대사 검사: 충전 건의 상태와 원장의 충전 기록(CHARGE / CHARGE_CANCEL)이 맞는가.
 *
 *   REQUESTED · ABORTED   기록 없음
 *   CHARGED · USED        CHARGE +금액
 *   CANCELED              CHARGE +금액, CHARGE_CANCEL -금액
 */
class ChargeLedgerCheckerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 14);
    private static final LocalDateTime ON_TARGET_DATE = TARGET_DATE.atTime(15, 0);
    private static final LocalDateTime PREVIOUS_DAY = TARGET_DATE.minusDays(1).atTime(20, 0);

    private LedgerStore ledger;
    private ChargeLedgerChecker checker;

    private final List<Charge> charges = new ArrayList<>();
    // 대상일에 지갑에 반영되거나 취소된 충전 건의 id
    private final List<Long> changedChargeIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ledger = new LedgerStore();
        ChargeRepository chargeRepository = mock(ChargeRepository.class);
        when(chargeRepository.findIdsChargedOrCanceledBetween(any(), any()))
                .thenAnswer(invocation -> new ArrayList<>(changedChargeIds));
        when(chargeRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            Set<Long> wanted = new HashSet<>();
            ids.forEach(wanted::add);
            return charges.stream().filter(charge -> wanted.contains(charge.getId())).toList();
        });
        checker = new ChargeLedgerChecker(chargeRepository, ledger.repository);
    }

    // 대상일에 상태가 바뀐 충전 건
    private Charge chargeChangedToday(long id, ChargeStatus status, long amount) {
        Charge charge = charge(id, status, amount);
        changedChargeIds.add(id);
        return charge;
    }

    // 대상일에 상태가 바뀌지 않은 충전 건 (원장 기록 쪽에서만 대상으로 잡힌다)
    private Charge charge(long id, ChargeStatus status, long amount) {
        Charge charge = ChargeFixture.charge(id, "CHG-" + id, status, amount);
        charges.add(charge);
        return charge;
    }

    private static DetectedMismatchDto only(LedgerCheckResult result) {
        assertThat(result.mismatches()).hasSize(1);
        return result.mismatches().get(0);
    }

    // ===== 일치 =====

    static Stream<Arguments> consistentCases() {
        return Stream.of(
                Arguments.of(REQUESTED, false, false),
                Arguments.of(ABORTED, false, false),
                Arguments.of(CHARGED, true, false),
                Arguments.of(USED, true, false),
                Arguments.of(CANCELED, true, true));
    }

    @ParameterizedTest
    @MethodSource("consistentCases")
    @DisplayName("상태에 맞는 원장 기록만 있으면 일치")
    void matched(ChargeStatus status, boolean chargeRecorded, boolean cancelRecorded) {
        chargeChangedToday(1L, status, 10_000);
        if (chargeRecorded) {
            ledger.add(PAYMENT, 1L, CHARGE, 10_000, ON_TARGET_DATE);
        }
        if (cancelRecorded) {
            ledger.add(PAYMENT, 1L, CHARGE_CANCEL, -10_000, ON_TARGET_DATE);
        }

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("충전은 전날, 취소는 대상일: 그 충전 건의 원장 기록 전체로 판정해 일치")
    void chargedPreviousDayCanceledOnTargetDate() {
        chargeChangedToday(1L, CANCELED, 10_000);
        ledger.add(PAYMENT, 1L, CHARGE, 10_000, PREVIOUS_DAY);
        ledger.add(PAYMENT, 1L, CHARGE_CANCEL, -10_000, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
    }

    // ===== 대상 =====

    @Test
    @DisplayName("대상일에 상태도 안 바뀌고 원장 기록도 생기지 않은 충전 건은 대상이 아니다")
    void untouchedChargeIsNotChecked() {
        charge(1L, CHARGED, 10_000);                                 // 전날 충전된 건. 원장 기록이 없어 어긋나 있지만
        ledger.add(PAYMENT, 2L, CHARGE, 5_000, PREVIOUS_DAY);        // 대상일에 일어난 일이 아니다

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(0);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("상태 변경과 원장 기록 양쪽에서 잡힌 충전 건은 한 번만 센다")
    void countedOnce() {
        chargeChangedToday(1L, CHARGED, 10_000);
        ledger.add(PAYMENT, 1L, CHARGE, 10_000, ON_TARGET_DATE);
        chargeChangedToday(2L, CHARGED, 20_000);
        ledger.add(PAYMENT, 2L, CHARGE, 20_000, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(2);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("대상이 1,200건이면 원장 기록을 500건씩 나눠 조회한다")
    void queriesInChunks() {
        for (long id = 1; id <= 1_200; id++) {
            chargeChangedToday(id, CHARGED, 1_000);
            ledger.add(PAYMENT, id, CHARGE, 1_000, ON_TARGET_DATE);
        }

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1_200);
        assertThat(result.mismatches()).isEmpty();
        assertThat(ledger.inQuerySizes).containsExactly(500, 500, 200);
    }

    // ===== 불일치 =====

    @Test
    @DisplayName("CHARGED 인데 CHARGE 기록이 없음: 충전 건은 돈이 들어왔다는데 원장에는 없다")
    void chargedWithoutRecord() {
        chargeChangedToday(1L, CHARGED, 10_000);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.type()).isEqualTo(CHARGE_LEDGER_MISMATCH);
        assertThat(mismatch.targetType()).isEqualTo(MismatchTargetType.CHARGE);
        assertThat(mismatch.targetKey()).isEqualTo("1");
        assertThat(mismatch.pgOrderId()).isNull();
        assertThat(mismatch.expectedState()).isEqualTo("NONE");      // 원장
        assertThat(mismatch.expectedAmount()).isEqualTo(0L);
        assertThat(mismatch.actualState()).isEqualTo("CHARGED");     // 충전 건
        assertThat(mismatch.actualAmount()).isEqualTo(10_000L);
        assertThat(mismatch.detail()).contains("CHARGED", "10000", "없음");
    }

    @Test
    @DisplayName("REQUESTED 인데 CHARGE 기록이 있음: 원장 기록 쪽에서 잡힌다")
    void recordWithoutStatusChange() {
        charge(1L, REQUESTED, 10_000);
        ledger.add(PAYMENT, 1L, CHARGE, 10_000, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        DetectedMismatchDto mismatch = only(result);
        assertThat(mismatch.expectedState()).isEqualTo("CHARGE");
        assertThat(mismatch.expectedAmount()).isEqualTo(10_000L);
        assertThat(mismatch.actualState()).isEqualTo("REQUESTED");
        assertThat(mismatch.actualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("CANCELED 인데 CHARGE_CANCEL 기록이 없음: 취소됐다는데 원장에는 돈이 남아 있다")
    void canceledWithoutCancelRecord() {
        chargeChangedToday(1L, CANCELED, 10_000);
        ledger.add(PAYMENT, 1L, CHARGE, 10_000, PREVIOUS_DAY);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("CHARGE");
        assertThat(mismatch.expectedAmount()).isEqualTo(10_000L);
        assertThat(mismatch.actualState()).isEqualTo("CANCELED");
        assertThat(mismatch.actualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("CHARGED 인데 CHARGE_CANCEL 기록까지 있음: 원장에서는 돈이 나갔는데 충전 건은 그대로다")
    void cancelRecordWithoutStatusChange() {
        charge(1L, CHARGED, 10_000);
        ledger.add(PAYMENT, 1L, CHARGE, 10_000, PREVIOUS_DAY);
        ledger.add(PAYMENT, 1L, CHARGE_CANCEL, -10_000, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("CHARGE+CHARGE_CANCEL");
        assertThat(mismatch.expectedAmount()).isEqualTo(0L);
        assertThat(mismatch.actualState()).isEqualTo("CHARGED");
        assertThat(mismatch.actualAmount()).isEqualTo(10_000L);
        assertThat(mismatch.detail()).contains("CHARGE 10000", "CHARGE_CANCEL -10000");
    }

    @Test
    @DisplayName("기록은 있는데 금액이 충전 금액과 다르면 불일치")
    void amountDiffers() {
        chargeChangedToday(1L, CHARGED, 10_000);
        ledger.add(PAYMENT, 1L, CHARGE, 9_000, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("CHARGE");
        assertThat(mismatch.expectedAmount()).isEqualTo(9_000L);
        assertThat(mismatch.actualState()).isEqualTo("CHARGED");
        assertThat(mismatch.actualAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("원장 기록이 가리키는 충전 건이 없으면 불일치. 충전 건 쪽 값은 비운다")
    void recordPointsToMissingCharge() {
        ledger.add(PAYMENT, 404L, CHARGE, 10_000, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.targetKey()).isEqualTo("404");
        assertThat(mismatch.expectedState()).isEqualTo("CHARGE");
        assertThat(mismatch.expectedAmount()).isEqualTo(10_000L);
        assertThat(mismatch.actualState()).isEqualTo("NOT_FOUND");
        assertThat(mismatch.actualAmount()).isNull();
    }

    @Test
    @DisplayName("여러 건 중 어긋난 건만 불일치로 남는다")
    void onlyMismatchedOnesAreReported() {
        chargeChangedToday(1L, CHARGED, 10_000);
        ledger.add(PAYMENT, 1L, CHARGE, 10_000, ON_TARGET_DATE);
        chargeChangedToday(2L, CHARGED, 20_000);                     // 기록 없음
        chargeChangedToday(3L, CANCELED, 30_000);
        ledger.add(PAYMENT, 3L, CHARGE, 30_000, ON_TARGET_DATE);
        ledger.add(PAYMENT, 3L, CHARGE_CANCEL, -30_000, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(3);
        assertThat(only(result).targetKey()).isEqualTo("2");
    }
}
