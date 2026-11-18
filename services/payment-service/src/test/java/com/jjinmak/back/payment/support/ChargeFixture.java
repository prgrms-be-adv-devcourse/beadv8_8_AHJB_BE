package com.jjinmak.back.payment.support;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

/**
 * 테스트용 충전 건. 대사는 충전 건의 id · 주문번호 · 상태 · 금액 · 승인/취소 시각만 보므로
 * 지갑이나 결제 없이 그 값들만 채운 Charge 를 만든다.
 */
public final class ChargeFixture {

    private static final Long HOLDER_ID = 9_001L;

    private ChargeFixture() {
    }

    public static Charge charge(long id, String pgOrderId, ChargeStatus status, long amount) {
        return charge(id, pgOrderId, status, amount, null, null);
    }

    public static Charge charge(long id, String pgOrderId, ChargeStatus status, long amount,
                                LocalDateTime approvedAt, LocalDateTime canceledAt) {
        Charge charge = Charge.requestManual(null, HOLDER_ID, amount);
        ReflectionTestUtils.setField(charge, "id", id);
        ReflectionTestUtils.setField(charge, "pgOrderId", pgOrderId);
        ReflectionTestUtils.setField(charge, "status", status);
        ReflectionTestUtils.setField(charge, "approvedAt", approvedAt);
        ReflectionTestUtils.setField(charge, "canceledAt", canceledAt);
        return charge;
    }
}
