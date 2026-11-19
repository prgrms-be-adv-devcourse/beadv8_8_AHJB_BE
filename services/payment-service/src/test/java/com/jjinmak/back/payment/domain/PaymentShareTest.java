package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_PLAN_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 금액 구성을 주문별로 나누는 규칙.
 */
class PaymentShareTest {

    private static void assertAllocation(PaymentShare total, List<Long> itemAmounts, List<PaymentShare> shares) {
        assertThat(shares).hasSameSizeAs(itemAmounts);
        for (int i = 0; i < shares.size(); i++) {
            PaymentShare share = shares.get(i);
            assertThat(share.total()).as("%d번째 주문의 몫 합", i).isEqualTo(itemAmounts.get(i));
            assertThat(share.pointAmount()).isBetween(0L, itemAmounts.get(i));
            assertThat(share.depositAmount()).isNotNegative();
            assertThat(share.pgAmount()).isNotNegative();
        }
        assertThat(shares.stream().mapToLong(PaymentShare::pointAmount).sum()).isEqualTo(total.pointAmount());
        assertThat(shares.stream().mapToLong(PaymentShare::depositAmount).sum()).isEqualTo(total.depositAmount());
        assertThat(shares.stream().mapToLong(PaymentShare::pgAmount).sum()).isEqualTo(total.pgAmount());
    }

    @Test
    @DisplayName("전액 예치금이면 주문마다 주문 금액 전부가 예치금 몫이다")
    void depositOnly() {
        PaymentShare total = PaymentShare.depositOnly(45_500);

        List<PaymentShare> shares = total.allocate(List.of(33_000L, 12_500L));

        assertThat(shares).containsExactly(new PaymentShare(0, 33_000, 0), new PaymentShare(0, 12_500, 0));
    }

    @Test
    @DisplayName("포인트 + 예치금: 포인트를 주문 금액에 비례해 나누고 나머지를 예치금 몫으로 둔다")
    void pointAndDeposit() {
        PaymentShare total = new PaymentShare(10_000, 40_000, 0);

        List<PaymentShare> shares = total.allocate(List.of(30_000L, 20_000L));

        assertThat(shares).containsExactly(new PaymentShare(6_000, 24_000, 0), new PaymentShare(4_000, 16_000, 0));
    }

    @Test
    @DisplayName("포인트 + 직접 결제: 나머지는 직접 결제 몫이 되고 예치금 몫은 없다")
    void pointAndDirect() {
        PaymentShare total = new PaymentShare(10_000, 0, 40_000);

        List<PaymentShare> shares = total.allocate(List.of(30_000L, 20_000L));

        assertThat(shares).containsExactly(new PaymentShare(6_000, 0, 24_000), new PaymentShare(4_000, 0, 16_000));
    }

    @Test
    @DisplayName("나누어떨어지지 않아도 주문별 몫의 합이 총액과 정확히 같다")
    void remainderIsNotLost() {
        PaymentShare total = new PaymentShare(1_000, 0, 9_000);
        List<Long> itemAmounts = List.of(3_333L, 3_333L, 3_334L);

        List<PaymentShare> shares = total.allocate(itemAmounts);

        assertAllocation(total, itemAmounts, shares);
        assertThat(shares).extracting(PaymentShare::pointAmount).containsExactly(333L, 333L, 334L);
    }

    @Test
    @DisplayName("마지막 주문 금액이 작아도 포인트 몫이 그 주문 금액을 넘지 않는다")
    void lastSmallItemIsNotOverAllocated() {
        // 주문마다 따로 절사하고 끝전을 마지막에 몰면 마지막 주문(1원)에 포인트 2원이 배정된다
        PaymentShare total = new PaymentShare(10, 1, 0);
        List<Long> itemAmounts = List.of(5L, 5L, 1L);

        List<PaymentShare> shares = total.allocate(itemAmounts);

        assertAllocation(total, itemAmounts, shares);
    }

    @Test
    @DisplayName("포인트로 전액을 내면 주문마다 주문 금액 전부가 포인트 몫이다")
    void pointOnly() {
        PaymentShare total = new PaymentShare(45_500, 0, 0);

        List<PaymentShare> shares = total.allocate(List.of(33_000L, 12_500L));

        assertThat(shares).containsExactly(new PaymentShare(33_000, 0, 0), new PaymentShare(12_500, 0, 0));
    }

    @Test
    @DisplayName("여러 금액 조합에서 항상 합이 맞고 음수 몫이 없다")
    void allocationInvariantsHold() {
        List<List<Long>> orders = List.of(
                List.of(1L), List.of(7L, 3L), List.of(1L, 1L, 1L), List.of(99_999L, 1L),
                List.of(12_345L, 678L, 90_123L, 4L), List.of(1_000_000L, 333L, 77_777L));

        for (List<Long> itemAmounts : orders) {
            long sum = itemAmounts.stream().mapToLong(Long::longValue).sum();
            for (long point : new long[]{0, 1, sum / 3, sum / 2, sum - 1, sum}) {
                if (point < 0 || point > sum) continue;
                PaymentShare withDeposit = new PaymentShare(point, sum - point, 0);
                assertAllocation(withDeposit, itemAmounts, withDeposit.allocate(itemAmounts));

                PaymentShare withPg = new PaymentShare(point, 0, sum - point);
                assertAllocation(withPg, itemAmounts, withPg.allocate(itemAmounts));
            }
        }
    }

    @Test
    @DisplayName("예치금과 직접 결제를 함께 쓰는 구성은 나눌 수 없다 (허용하지 않는 조합)")
    void depositWithDirectIsRejected() {
        PaymentShare total = new PaymentShare(0, 10_000, 20_000);

        assertThatThrownBy(() -> total.allocate(List.of(30_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_PLAN_MISMATCH);
    }

    @Test
    @DisplayName("주문 금액 합이 결제 총액과 다르면 나눌 수 없다")
    void itemSumMustMatchTotal() {
        PaymentShare total = PaymentShare.depositOnly(30_000);

        assertThatThrownBy(() -> total.allocate(List.of(10_000L, 10_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_PLAN_MISMATCH);
    }
}
