package com.jjinmak.payment.app.dto;

import com.jjinmak.payment.app.dto.PgPaymentState.Kind;
import com.jjinmak.payment.out.dto.TossPaymentResponse;
import com.jjinmak.payment.out.dto.TossTransactionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 거래 · 결제를 대사용 상태(PgPaymentState)로 바꾸는 규칙.
 *   거래 목록 : 승인 줄(DONE)과 취소 줄(CANCELED / PARTIAL_CANCELED)을 status 로 나눠 합한다
 *   단건 조회 : 토스가 알려주는 status 와 남은 금액을 그대로 쓴다
 */
class PgPaymentStateTest {

    private static final String ORDER_ID = "CHG-test";

    private static TossTransactionResponse transaction(String orderId, String status, long amount) {
        return new TossTransactionResponse("tx-" + orderId + "-" + status + "-" + amount, "pk-" + orderId, orderId,
                status, amount, "2026-10-21T15:00:00+09:00");
    }

    private static TossPaymentResponse payment(String status, long totalAmount, long balanceAmount) {
        return new TossPaymentResponse("pk-test", ORDER_ID, "찐막 예치금 충전", status, totalAmount, balanceAmount,
                "간편결제", null, null, null, null, null);
    }

    // ===== 거래 목록에서 =====

    @Test
    @DisplayName("거래가 없으면 NONE")
    void noTransactions() {
        assertThat(PgPaymentState.fromTransactions(ORDER_ID, List.of()).kind()).isEqualTo(Kind.NONE);
        assertThat(PgPaymentState.fromTransactions(ORDER_ID, null).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    @DisplayName("승인 줄만 있으면 APPROVED 이고 남은 금액은 승인 금액과 같다")
    void approvedOnly() {
        PgPaymentState state = PgPaymentState.fromTransactions(ORDER_ID, List.of(transaction(ORDER_ID, "DONE", 20_000)));

        assertThat(state.kind()).isEqualTo(Kind.APPROVED);
        assertThat(state.approvedAmount()).isEqualTo(20_000L);
        assertThat(state.balanceAmount()).isEqualTo(20_000L);
        assertThat(state.pgOrderId()).isEqualTo(ORDER_ID);
        assertThat(state.paymentKey()).isEqualTo("pk-" + ORDER_ID);
    }

    @Test
    @DisplayName("승인 뒤 전액 취소되면 CANCELED 이고 남은 금액은 0 이다 (취소 줄의 금액은 양수로 온다)")
    void approvedThenCanceled() {
        PgPaymentState state = PgPaymentState.fromTransactions(ORDER_ID, List.of(
                transaction(ORDER_ID, "DONE", 100_000),
                transaction(ORDER_ID, "CANCELED", 100_000)));

        assertThat(state.kind()).isEqualTo(Kind.CANCELED);
        assertThat(state.approvedAmount()).isEqualTo(100_000L);
        assertThat(state.balanceAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("취소 줄의 금액이 음수로 와도 결과는 같다")
    void negativeCancelAmount() {
        PgPaymentState state = PgPaymentState.fromTransactions(ORDER_ID, List.of(
                transaction(ORDER_ID, "DONE", 7_000),
                transaction(ORDER_ID, "CANCELED", -7_000)));

        assertThat(state.kind()).isEqualTo(Kind.CANCELED);
        assertThat(state.balanceAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("승인 줄 없이 취소 줄만 있으면 UNKNOWN 이다 (승인이 다른 날이라 그날 거래만으로는 알 수 없다)")
    void canceledOnly() {
        PgPaymentState state = PgPaymentState.fromTransactions(ORDER_ID, List.of(transaction(ORDER_ID, "CANCELED", 50_000)));

        assertThat(state.kind()).isEqualTo(Kind.UNKNOWN);
        assertThat(state.isUnknown()).isTrue();
    }

    @Test
    @DisplayName("일부만 취소되면 PARTIAL_CANCELED 이고 남은 금액은 승인 - 취소다")
    void partiallyCanceled() {
        PgPaymentState state = PgPaymentState.fromTransactions(ORDER_ID, List.of(
                transaction(ORDER_ID, "DONE", 10_000),
                transaction(ORDER_ID, "PARTIAL_CANCELED", 3_000)));

        assertThat(state.kind()).isEqualTo(Kind.PARTIAL_CANCELED);
        assertThat(state.balanceAmount()).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("부분 취소가 이어져 전액이 취소되면 CANCELED 다")
    void partialCancelsUpToFullAmount() {
        PgPaymentState state = PgPaymentState.fromTransactions(ORDER_ID, List.of(
                transaction(ORDER_ID, "DONE", 10_000),
                transaction(ORDER_ID, "PARTIAL_CANCELED", 3_000),
                transaction(ORDER_ID, "CANCELED", 7_000)));

        assertThat(state.kind()).isEqualTo(Kind.CANCELED);
        assertThat(state.balanceAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("승인 · 취소가 아닌 줄은 돈이 움직이지 않은 것이라 무시한다")
    void ignoresRowsWithoutMoneyMovement() {
        PgPaymentState onlyFailed = PgPaymentState.fromTransactions(ORDER_ID, List.of(
                transaction(ORDER_ID, "ABORTED", 5_000),
                transaction(ORDER_ID, "EXPIRED", 5_000)));
        PgPaymentState failedThenApproved = PgPaymentState.fromTransactions(ORDER_ID, List.of(
                transaction(ORDER_ID, "ABORTED", 5_000),
                transaction(ORDER_ID, "DONE", 5_000)));

        assertThat(onlyFailed.kind()).isEqualTo(Kind.NONE);
        assertThat(failedThenApproved.kind()).isEqualTo(Kind.APPROVED);
        assertThat(failedThenApproved.balanceAmount()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("2026-10-21 실제 토스 응답 11줄은 주문번호 8건으로 접힌다 (승인 3, 전액 취소 3, 판단 불가 2)")
    void realTransactionsOfOneDay() {
        List<TossTransactionResponse> day = List.of(
                transaction("CHG-a31f", "CANCELED", 50_000),      // 전날 승인, 이날 취소
                transaction("CHG-6466", "DONE", 10_000),
                transaction("CHG-e2da", "DONE", 100_000),
                transaction("CHG-9954", "DONE", 150_000),
                transaction("CHG-e2da", "CANCELED", 100_000),
                transaction("CHG-9954", "CANCELED", 150_000),
                transaction("CHG-fc2d", "CANCELED", 1_000),       // 전날 승인, 이날 취소
                transaction("CHG-6466", "CANCELED", 10_000),
                transaction("CHG-44a1", "DONE", 20_000),
                transaction("CHG-e11d", "DONE", 33_000),
                transaction("CHG-75a8", "DONE", 300_000));

        Map<String, List<TossTransactionResponse>> byOrderId = new LinkedHashMap<>();
        for (TossTransactionResponse transaction : day) {
            byOrderId.computeIfAbsent(transaction.orderId(), orderId -> new ArrayList<>()).add(transaction);
        }
        Map<String, Kind> kinds = new LinkedHashMap<>();
        byOrderId.forEach((orderId, transactions) ->
                kinds.put(orderId, PgPaymentState.fromTransactions(orderId, transactions).kind()));

        assertThat(kinds).hasSize(8);
        assertThat(kinds.get("CHG-a31f")).isEqualTo(Kind.UNKNOWN);
        assertThat(kinds.get("CHG-fc2d")).isEqualTo(Kind.UNKNOWN);
        assertThat(kinds.get("CHG-6466")).isEqualTo(Kind.CANCELED);
        assertThat(kinds.get("CHG-e2da")).isEqualTo(Kind.CANCELED);
        assertThat(kinds.get("CHG-9954")).isEqualTo(Kind.CANCELED);
        assertThat(kinds.get("CHG-44a1")).isEqualTo(Kind.APPROVED);
        assertThat(kinds.get("CHG-e11d")).isEqualTo(Kind.APPROVED);
        assertThat(kinds.get("CHG-75a8")).isEqualTo(Kind.APPROVED);
    }

    // ===== 단건 조회에서 =====

    @Test
    @DisplayName("단건 조회: DONE 은 APPROVED, 남은 금액은 토스가 준 값 그대로")
    void paymentDone() {
        PgPaymentState state = PgPaymentState.fromPayment(payment("DONE", 20_000, 20_000));

        assertThat(state.kind()).isEqualTo(Kind.APPROVED);
        assertThat(state.approvedAmount()).isEqualTo(20_000L);
        assertThat(state.balanceAmount()).isEqualTo(20_000L);
        assertThat(state.pgOrderId()).isEqualTo(ORDER_ID);
        assertThat(state.paymentKey()).isEqualTo("pk-test");
    }

    @Test
    @DisplayName("단건 조회: CANCELED 는 CANCELED, 남은 금액 0 (CHG-e2da 실제 응답과 같은 값)")
    void paymentCanceled() {
        PgPaymentState state = PgPaymentState.fromPayment(payment("CANCELED", 100_000, 0));

        assertThat(state.kind()).isEqualTo(Kind.CANCELED);
        assertThat(state.approvedAmount()).isEqualTo(100_000L);
        assertThat(state.balanceAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("단건 조회: PARTIAL_CANCELED 는 PARTIAL_CANCELED")
    void paymentPartiallyCanceled() {
        PgPaymentState state = PgPaymentState.fromPayment(payment("PARTIAL_CANCELED", 20_000, 5_000));

        assertThat(state.kind()).isEqualTo(Kind.PARTIAL_CANCELED);
        assertThat(state.balanceAmount()).isEqualTo(5_000L);
    }

    @ParameterizedTest(name = "단건 조회: {0} 은 돈이 움직이지 않았으므로 NONE")
    @MethodSource("statusesWithoutMoneyMovement")
    void paymentWithoutMoneyMovement(String status) {
        PgPaymentState state = PgPaymentState.fromPayment(payment(status, 20_000, 20_000));

        assertThat(state.kind()).isEqualTo(Kind.NONE);
        assertThat(state.isUnknown()).isFalse();
        assertThat(state.pgOrderId()).isEqualTo(ORDER_ID);
        assertThat(state.balanceAmount()).isEqualTo(0L);
    }

    static Stream<String> statusesWithoutMoneyMovement() {
        return Stream.of("READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT", "ABORTED", "EXPIRED");
    }
}
