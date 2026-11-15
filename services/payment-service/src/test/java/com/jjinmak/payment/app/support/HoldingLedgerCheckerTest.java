package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.Holding;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.out.repository.HoldingRepository;
import com.jjinmak.payment.support.HoldingFixture;
import com.jjinmak.payment.support.LedgerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jjinmak.payment.domain.enums.MismatchType.HOLDING_LEDGER_MISMATCH;
import static com.jjinmak.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.FEE_PAY;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.FEE_RECEIVE;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.HOLD_PAY;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.HOLD_RECEIVE;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.REFUND_PAY;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.REFUND_RECEIVE;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.SETTLEMENT_PAY;
import static com.jjinmak.payment.domain.enums.WalletTransactionType.SETTLEMENT_RECEIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 원장 대사 검사: 홀딩의 상태와 그 주문의 원장 기록이 맞는가.
 *
 *   HELD · CONFIRMED   HOLD_PAY -금액, HOLD_RECEIVE +금액
 *   REFUNDED           위 + REFUND_PAY -금액, REFUND_RECEIVE +금액
 *   RELEASED           위 + SETTLEMENT_PAY -정산액, SETTLEMENT_RECEIVE +정산액 (+ 수수료가 있으면 FEE_PAY -수수료, FEE_RECEIVE +수수료)
 *
 * 여기서 쓰는 홀딩: 낙찰가 30,000 + 배송비 3,000 = 홀딩 금액 33,000, 수수료 3,000, 정산액 30,000.
 */
class HoldingLedgerCheckerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 14);
    private static final LocalDateTime ON_TARGET_DATE = TARGET_DATE.atTime(15, 0);
    private static final LocalDateTime PREVIOUS_DAY = TARGET_DATE.minusDays(1).atTime(20, 0);

    private static final long WINNING_PRICE = 30_000;
    private static final long DELIVERY_FEE = 3_000;
    private static final long FEE = 3_000;
    private static final long AMOUNT = 33_000;        // 낙찰가 + 배송비
    private static final long SETTLEMENT = 30_000;    // 홀딩 금액 - 수수료

    private LedgerStore ledger;
    private HoldingLedgerChecker checker;

    private final List<Holding> holdings = new ArrayList<>();
    // 대상일에 만들어지거나 환불 · 정산된 홀딩의 주문 id
    private final List<Long> changedOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ledger = new LedgerStore();
        HoldingRepository holdingRepository = mock(HoldingRepository.class);
        when(holdingRepository.findOrderIdsChangedBetween(any(), any()))
                .thenAnswer(invocation -> new ArrayList<>(changedOrderIds));
        when(holdingRepository.findByOrderIdIn(any())).thenAnswer(invocation -> {
            Collection<Long> orderIds = invocation.getArgument(0);
            return holdings.stream().filter(holding -> orderIds.contains(holding.getOrderId())).toList();
        });
        checker = new HoldingLedgerChecker(holdingRepository, ledger.repository);
    }

    // 대상일에 상태가 바뀐 홀딩
    private void changedToday(Holding holding) {
        holdings.add(holding);
        changedOrderIds.add(holding.getOrderId());
    }

    // 결제: 구매자 -금액, ESCROW +금액
    private void recordHold(long orderId, LocalDateTime at) {
        ledger.add(ORDER, orderId, HOLD_PAY, -AMOUNT, at);
        ledger.add(ORDER, orderId, HOLD_RECEIVE, AMOUNT, at);
    }

    // 환불: ESCROW -금액, 구매자 +금액
    private void recordRefund(long orderId, LocalDateTime at) {
        ledger.add(ORDER, orderId, REFUND_PAY, -AMOUNT, at);
        ledger.add(ORDER, orderId, REFUND_RECEIVE, AMOUNT, at);
    }

    // 정산: ESCROW -정산액, 판매자 +정산액
    private void recordSettlement(long orderId, long settlementAmount, LocalDateTime at) {
        ledger.add(ORDER, orderId, SETTLEMENT_PAY, -settlementAmount, at);
        ledger.add(ORDER, orderId, SETTLEMENT_RECEIVE, settlementAmount, at);
    }

    // 수수료: ESCROW -수수료, FEE +수수료
    private void recordFee(long orderId, LocalDateTime at) {
        ledger.add(ORDER, orderId, FEE_PAY, -FEE, at);
        ledger.add(ORDER, orderId, FEE_RECEIVE, FEE, at);
    }

    private static DetectedMismatchDto only(LedgerCheckResult result) {
        assertThat(result.mismatches()).hasSize(1);
        return result.mismatches().get(0);
    }

    // ===== 일치 =====

    @Test
    @DisplayName("HELD: 결제 기록만 있으면 일치")
    void heldMatches() {
        changedToday(HoldingFixture.held(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("CONFIRMED: 구매 확정은 원장에 남지 않으므로 결제 기록만 있으면 일치")
    void confirmedMatches() {
        changedToday(HoldingFixture.confirmed(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, ON_TARGET_DATE);

        assertThat(checker.check(TARGET_DATE).mismatches()).isEmpty();
    }

    @Test
    @DisplayName("REFUNDED: 결제 + 환불 기록이 있으면 일치")
    void refundedMatches() {
        changedToday(HoldingFixture.refunded(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, ON_TARGET_DATE);
        recordRefund(1L, ON_TARGET_DATE);

        assertThat(checker.check(TARGET_DATE).mismatches()).isEmpty();
    }

    @Test
    @DisplayName("RELEASED: 결제 + 정산 + 수수료 기록이 있으면 일치")
    void releasedMatches() {
        changedToday(HoldingFixture.released(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, ON_TARGET_DATE);
        recordSettlement(1L, SETTLEMENT, ON_TARGET_DATE);
        recordFee(1L, ON_TARGET_DATE);

        assertThat(checker.check(TARGET_DATE).mismatches()).isEmpty();
    }

    @Test
    @DisplayName("RELEASED 인데 수수료가 0 이면 수수료 기록 없이 정산 기록만으로 일치")
    void releasedWithoutFeeMatches() {
        changedToday(HoldingFixture.released(1L, WINNING_PRICE, DELIVERY_FEE, 0));
        recordHold(1L, ON_TARGET_DATE);
        recordSettlement(1L, AMOUNT, ON_TARGET_DATE);                // 수수료가 없어 홀딩 금액 전부가 정산액

        assertThat(checker.check(TARGET_DATE).mismatches()).isEmpty();
    }

    @Test
    @DisplayName("결제는 전날, 정산은 대상일: 그 주문의 원장 기록 전체로 판정해 일치")
    void heldPreviousDayReleasedOnTargetDate() {
        changedToday(HoldingFixture.released(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, PREVIOUS_DAY);
        recordSettlement(1L, SETTLEMENT, ON_TARGET_DATE);
        recordFee(1L, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
    }

    // ===== 대상 =====

    @Test
    @DisplayName("대상일에 상태도 안 바뀌고 원장 기록도 생기지 않은 홀딩은 대상이 아니다")
    void untouchedHoldingIsNotChecked() {
        holdings.add(HoldingFixture.held(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, PREVIOUS_DAY);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(0);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("대상이 1,200건이면 원장 기록을 500건씩 나눠 조회한다")
    void queriesInChunks() {
        for (long orderId = 1; orderId <= 1_200; orderId++) {
            changedToday(HoldingFixture.held(orderId, WINNING_PRICE, DELIVERY_FEE, FEE));
            recordHold(orderId, ON_TARGET_DATE);
        }

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1_200);
        assertThat(result.mismatches()).isEmpty();
        assertThat(ledger.inQuerySizes).containsExactly(500, 500, 200);
    }

    // ===== 불일치 =====

    @Test
    @DisplayName("HELD 인데 결제 기록이 없음: 홀딩은 돈을 잡고 있다는데 원장에는 없다")
    void heldWithoutRecord() {
        changedToday(HoldingFixture.held(1L, WINNING_PRICE, DELIVERY_FEE, FEE));

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.type()).isEqualTo(HOLDING_LEDGER_MISMATCH);
        assertThat(mismatch.targetType()).isEqualTo(MismatchTargetType.HOLDING);
        assertThat(mismatch.targetKey()).isEqualTo("1");             // 주문 id
        assertThat(mismatch.pgOrderId()).isNull();
        assertThat(mismatch.expectedState()).isEqualTo("NONE");      // 원장
        assertThat(mismatch.expectedAmount()).isEqualTo(0L);
        assertThat(mismatch.actualState()).isEqualTo("HELD");        // 홀딩
        assertThat(mismatch.actualAmount()).isEqualTo(AMOUNT);
        assertThat(mismatch.detail()).contains("HELD", "33000", "없음");
    }

    @Test
    @DisplayName("결제 기록이 한쪽만 있음: 구매자에게서 빠졌는데 ESCROW 에 들어온 기록이 없다")
    void onlyOneSideRecorded() {
        changedToday(HoldingFixture.held(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        ledger.add(ORDER, 1L, HOLD_PAY, -AMOUNT, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("HELD");
        assertThat(mismatch.expectedAmount()).isEqualTo(0L);         // ESCROW 쪽 기록이 없어 원장상 남은 돈은 0
        assertThat(mismatch.actualAmount()).isEqualTo(AMOUNT);
        assertThat(mismatch.detail()).contains("HOLD_PAY -33000");
    }

    @Test
    @DisplayName("REFUNDED 인데 환불 기록이 없음: 환불했다는데 원장에는 돈이 ESCROW 에 남아 있다")
    void refundedWithoutRefundRecord() {
        changedToday(HoldingFixture.refunded(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, PREVIOUS_DAY);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("HELD");
        assertThat(mismatch.expectedAmount()).isEqualTo(AMOUNT);
        assertThat(mismatch.actualState()).isEqualTo("REFUNDED");
        assertThat(mismatch.actualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("HELD 인데 환불 기록이 있음: 원장 기록 쪽에서 잡힌다")
    void refundRecordWithoutStatusChange() {
        holdings.add(HoldingFixture.held(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, PREVIOUS_DAY);
        recordRefund(1L, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        DetectedMismatchDto mismatch = only(result);
        assertThat(mismatch.expectedState()).isEqualTo("REFUNDED");
        assertThat(mismatch.expectedAmount()).isEqualTo(0L);
        assertThat(mismatch.actualState()).isEqualTo("HELD");
        assertThat(mismatch.actualAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("RELEASED 인데 수수료 기록이 빠짐: 수수료만큼이 원장상 ESCROW 에 남아 있다")
    void releasedWithoutFeeRecord() {
        changedToday(HoldingFixture.released(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, PREVIOUS_DAY);
        recordSettlement(1L, SETTLEMENT, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("RELEASED");
        assertThat(mismatch.expectedAmount()).isEqualTo(FEE);
        assertThat(mismatch.actualState()).isEqualTo("RELEASED");
        assertThat(mismatch.actualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("RELEASED 인데 정산 기록의 금액이 정산액과 다르면 불일치")
    void settlementAmountDiffers() {
        changedToday(HoldingFixture.released(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, PREVIOUS_DAY);
        recordSettlement(1L, AMOUNT, ON_TARGET_DATE);                // 수수료를 떼지 않고 전액을 정산
        recordFee(1L, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("RELEASED");
        assertThat(mismatch.expectedAmount()).isEqualTo(-FEE);       // ESCROW 에서 수수료만큼 더 나갔다
        assertThat(mismatch.actualAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("환불 기록과 정산 기록이 둘 다 있으면 어떤 상태와도 맞지 않는다")
    void bothRefundAndSettlementRecorded() {
        changedToday(HoldingFixture.refunded(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, PREVIOUS_DAY);
        recordRefund(1L, ON_TARGET_DATE);
        recordSettlement(1L, SETTLEMENT, ON_TARGET_DATE);
        recordFee(1L, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.expectedState()).isEqualTo("REFUNDED+RELEASED");
        assertThat(mismatch.expectedAmount()).isEqualTo(-AMOUNT);    // 들어온 돈보다 홀딩 금액만큼 더 나갔다
        assertThat(mismatch.actualState()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("원장 기록이 가리키는 주문의 홀딩이 없으면 불일치. 홀딩 쪽 값은 비운다")
    void recordPointsToMissingHolding() {
        recordHold(404L, ON_TARGET_DATE);

        DetectedMismatchDto mismatch = only(checker.check(TARGET_DATE));

        assertThat(mismatch.targetKey()).isEqualTo("404");
        assertThat(mismatch.expectedState()).isEqualTo("HELD");
        assertThat(mismatch.expectedAmount()).isEqualTo(AMOUNT);
        assertThat(mismatch.actualState()).isEqualTo("NOT_FOUND");
        assertThat(mismatch.actualAmount()).isNull();
    }

    @Test
    @DisplayName("여러 주문 중 어긋난 주문만 불일치로 남는다")
    void onlyMismatchedOnesAreReported() {
        changedToday(HoldingFixture.held(1L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(1L, ON_TARGET_DATE);
        changedToday(HoldingFixture.refunded(2L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(2L, ON_TARGET_DATE);                              // 환불 기록 없음
        changedToday(HoldingFixture.released(3L, WINNING_PRICE, DELIVERY_FEE, FEE));
        recordHold(3L, ON_TARGET_DATE);
        recordSettlement(3L, SETTLEMENT, ON_TARGET_DATE);
        recordFee(3L, ON_TARGET_DATE);

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(3);
        assertThat(only(result).targetKey()).isEqualTo("2");
    }
}
