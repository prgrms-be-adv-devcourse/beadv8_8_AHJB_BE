package com.jjinmak.back.payment.domain;

import com.jjinmak.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_PLAN_MISMATCH;

/**
 * 금액 하나를 무엇으로 냈는지 (포인트 · 예치금 · 직접 결제). 결제 전체에도, 주문 1건의 몫에도 쓴다.
 *
 * 예치금과 직접 결제는 한 결제에서 함께 쓸 수 없으므로 depositAmount 와 pgAmount 중 하나는 항상 0 이다.
 */
public record PaymentShare(
        long pointAmount,
        long depositAmount,
        long pgAmount
) {
    public static PaymentShare depositOnly(long amount) {
        return new PaymentShare(0L, amount, 0L);
    }

    public long total() {
        return pointAmount + depositAmount + pgAmount;
    }

    /**
     * 결제 전체의 금액 구성을 주문별로 나눈다. itemAmounts 는 주문별 금액(낙찰가 + 배송비)이고 합이 total() 과 같아야 한다.
     *
     * 포인트를 주문 금액에 비례해 나누고, 주문 금액에서 포인트 몫을 뺀 나머지를 예치금(또는 직접 결제) 몫으로 둔다.
     * 포인트는 "앞에서부터 누적한 주문 금액 비율만큼"을 원 단위로 절사해 나눈다:
     *   n번째 주문까지의 포인트 누계 = 포인트 총액 × (n번째 주문까지의 금액 누계) / 결제 총액   (절사)
     * 이렇게 하면 주문별 포인트 몫의 합이 포인트 총액과 정확히 같고, 어느 주문도 자기 금액보다 많은 포인트를 받지 않는다.
     * (주문마다 따로 절사하고 끝전을 마지막 주문에 몰면, 마지막 주문 금액이 작을 때 끝전이 그 금액을 넘을 수 있다)
     */
    public List<PaymentShare> allocate(List<Long> itemAmounts) {
        if (depositAmount > 0 && pgAmount > 0) {
            throw new BusinessException(PAYMENT_PLAN_MISMATCH);      // 예치금 + 직접 결제는 허용하지 않는 조합
        }
        long total = total();
        long itemSum = itemAmounts.stream().mapToLong(Long::longValue).sum();
        if (total <= 0 || itemSum != total) {
            throw new BusinessException(PAYMENT_PLAN_MISMATCH);
        }
        boolean direct = pgAmount > 0;

        List<PaymentShare> shares = new ArrayList<>(itemAmounts.size());
        long amountSoFar = 0L;
        long pointSoFar = 0L;
        for (long itemAmount : itemAmounts) {
            amountSoFar += itemAmount;
            long pointUpToHere = pointAmount * amountSoFar / total;
            long point = pointUpToHere - pointSoFar;
            pointSoFar = pointUpToHere;

            long rest = itemAmount - point;
            shares.add(direct ? new PaymentShare(point, 0L, rest) : new PaymentShare(point, rest, 0L));
        }
        return shares;
    }
}
