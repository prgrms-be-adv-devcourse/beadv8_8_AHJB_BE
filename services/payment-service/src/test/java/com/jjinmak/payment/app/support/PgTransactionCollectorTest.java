package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.PgPaymentState;
import com.jjinmak.payment.app.dto.PgPaymentState.Kind;
import com.jjinmak.payment.exception.PgApiException;
import com.jjinmak.payment.support.FakeTossPaymentsClient;
import com.jjinmak.payment.support.FakeTossPaymentsClient.CursorMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static com.jjinmak.payment.exception.PaymentErrorCode.PG_INQUIRY_FAILED;
import static com.jjinmak.payment.exception.PaymentErrorCode.PG_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 토스 거래 수집.
 *   collect      : 대상일의 거래를 마지막 페이지까지 받아 주문번호별 상태로 접는다
 *   fetchCurrent : 주문번호 1건의 현재 상태를 단건 조회로 받는다
 */
class PgTransactionCollectorTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 21);

    private FakeTossPaymentsClient toss;
    private PgTransactionCollector collector;

    @BeforeEach
    void setUp() {
        toss = new FakeTossPaymentsClient();
        collector = new PgTransactionCollector(toss);
    }

    // 주문번호가 서로 다른 승인 거래를 count 줄 넣는다 (O1, O2, ...). 거래 키는 tx-1, tx-2, ...
    private void approvedOrders(int count) {
        for (int i = 1; i <= count; i++) {
            toss.approved("O" + i, 1_000);
        }
    }

    @Test
    @DisplayName("하루치 거래를 주문번호별 상태로 접는다. 거래가 온 순서를 유지한다")
    void collectFoldsByOrderId() {
        toss.canceled("A", 50_000)                               // 승인 없이 취소만
                .approved("B", 10_000)
                .approved("C", 100_000)
                .canceled("C", 100_000)
                .canceled("B", 10_000)
                .approved("D", 20_000);

        Map<String, PgPaymentState> states = collector.collect(TARGET_DATE);

        assertThat(states.keySet()).containsExactly("A", "B", "C", "D");
        assertThat(states.get("A").kind()).isEqualTo(Kind.UNKNOWN);
        assertThat(states.get("B").kind()).isEqualTo(Kind.CANCELED);
        assertThat(states.get("C").kind()).isEqualTo(Kind.CANCELED);
        assertThat(states.get("D").kind()).isEqualTo(Kind.APPROVED);
        assertThat(states.get("D").balanceAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("대상일 00:00:00 부터 23:59:59 까지를 첫 페이지부터 100건씩 조회한다")
    void collectRequestsWholeDay() {
        toss.approved("A", 1_000);

        collector.collect(TARGET_DATE);

        assertThat(toss.lastStart()).isEqualTo(LocalDateTime.of(2026, 10, 21, 0, 0, 0));
        assertThat(toss.lastEnd()).isEqualTo(LocalDateTime.of(2026, 10, 21, 23, 59, 59));
        assertThat(toss.lastLimit()).isEqualTo(PgTransactionCollector.PAGE_SIZE);
        assertThat(toss.requestedCursors()).hasSize(1);
        assertThat(toss.requestedCursors().get(0)).isNull();
    }

    @Test
    @DisplayName("거래가 없는 날은 빈 결과")
    void collectEmptyDay() {
        assertThat(collector.collect(TARGET_DATE)).isEmpty();
    }

    @Test
    @DisplayName("한 페이지를 넘으면 마지막 거래 키를 넘겨 다음 페이지를 받는다")
    void collectFollowsPages() {
        approvedOrders(250);

        Map<String, PgPaymentState> states = collector.collect(TARGET_DATE);

        assertThat(states).hasSize(250);
        assertThat(toss.requestedCursors()).containsExactly(null, "tx-100", "tx-200");
    }

    @Test
    @DisplayName("거래 수가 페이지 크기의 배수면 빈 페이지를 한 번 더 받고 끝낸다")
    void collectStopsOnEmptyPage() {
        approvedOrders(200);

        Map<String, PgPaymentState> states = collector.collect(TARGET_DATE);

        assertThat(states).hasSize(200);
        assertThat(toss.requestedCursors()).containsExactly(null, "tx-100", "tx-200");
    }

    @Test
    @DisplayName("같은 주문의 승인과 취소가 서로 다른 페이지에 있어도 하나의 상태로 합쳐진다")
    void collectMergesAcrossPages() {
        toss.approved("X", 7_000);
        approvedOrders(120);
        toss.canceled("X", 7_000);

        Map<String, PgPaymentState> states = collector.collect(TARGET_DATE);

        assertThat(states).hasSize(121);
        assertThat(states.get("X").kind()).isEqualTo(Kind.CANCELED);
        assertThat(states.get("X").balanceAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("다음 페이지에 직전 마지막 거래가 다시 포함돼 와도 같은 거래를 두 번 세지 않는다")
    void collectDeduplicatesRepeatedRows() {
        toss.cursorMode(CursorMode.INCLUSIVE);
        approvedOrders(250);

        Map<String, PgPaymentState> states = collector.collect(TARGET_DATE);

        assertThat(states).hasSize(250);
        assertThat(states.get("O100").approvedAmount()).isEqualTo(1_000L);
        assertThat(states.get("O200").approvedAmount()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("다음 페이지를 요청했는데 같은 페이지만 돌아오면 끝없이 반복하지 않고 실패한다")
    void collectFailsWhenPaginationDoesNotAdvance() {
        toss.cursorMode(CursorMode.IGNORED);
        approvedOrders(150);

        assertThatThrownBy(() -> collector.collect(TARGET_DATE))
                .isInstanceOf(PgApiException.class)
                .extracting("errorCode").isEqualTo(PG_INQUIRY_FAILED);
        assertThat(toss.requestedCursors()).hasSize(2);
    }

    @Test
    @DisplayName("돈이 움직이지 않은 주문번호와 주문번호가 없는 줄은 결과에 넣지 않는다")
    void collectSkipsRowsThatCannotBeMatched() {
        toss.transaction("FAILED-ONLY", "ABORTED", 5_000)
                .transaction(null, "DONE", 5_000)
                .approved("OK", 9_000);

        Map<String, PgPaymentState> states = collector.collect(TARGET_DATE);

        assertThat(states.keySet()).containsExactly("OK");
    }

    @Test
    @DisplayName("거래 목록 조회가 실패하면 예외를 그대로 던진다")
    void collectPropagatesFailure() {
        toss.failTransactions(new PgApiException(PG_UNAVAILABLE, "NETWORK", "timeout"));

        assertThatThrownBy(() -> collector.collect(TARGET_DATE))
                .isInstanceOf(PgApiException.class)
                .extracting("errorCode").isEqualTo(PG_UNAVAILABLE);
    }

    @Test
    @DisplayName("단건 조회 결과를 현재 상태로 돌려준다")
    void fetchCurrent() {
        toss.payment("CANCELED-ONE", "CANCELED", 100_000, 0)
                .payment("DONE-ONE", "DONE", 20_000, 20_000);

        assertThat(collector.fetchCurrent("CANCELED-ONE").kind()).isEqualTo(Kind.CANCELED);
        assertThat(collector.fetchCurrent("DONE-ONE").kind()).isEqualTo(Kind.APPROVED);
        assertThat(collector.fetchCurrent("DONE-ONE").balanceAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("토스에 결제 기록이 없으면 NONE 을 돌려준다")
    void fetchCurrentNotFound() {
        PgPaymentState state = collector.fetchCurrent("MISSING");

        assertThat(state.kind()).isEqualTo(Kind.NONE);
        assertThat(state.pgOrderId()).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("단건 조회가 통신 오류로 실패하면 NONE 으로 넘기지 않고 예외를 던진다")
    void fetchCurrentPropagatesFailure() {
        toss.failPayments(new PgApiException(PG_UNAVAILABLE, "NETWORK", "timeout"));

        assertThatThrownBy(() -> collector.fetchCurrent("ANY"))
                .isInstanceOf(PgApiException.class)
                .extracting("errorCode").isEqualTo(PG_UNAVAILABLE);
    }
}
