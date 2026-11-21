package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.domain.Payment;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 주문 그룹의 첫 결제 요청이 동시에 와 유니크 제약에 걸렸을 때만 한 번 더 실행하는지 확인한다.
 * 실제 DB 에서 그렇게 되는지는 PaymentRequestRaceIntegrationTest 가 본다.
 */
class PaymentCreateRaceSupportTest {

    private final PaymentCreateRaceSupport support = new PaymentCreateRaceSupport();
    private final AtomicInteger calls = new AtomicInteger();

    private static DataIntegrityViolationException violationOf(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key value"), constraintName);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }

    // 처음에는 failure 를 던지고, 다시 부르면 "second" 를 돌려준다
    private String failOnceWith(RuntimeException failure) {
        if (calls.incrementAndGet() == 1) {
            throw failure;
        }
        return "second";
    }

    @Test
    @DisplayName("충돌이 없으면 한 번만 실행하고 결과를 그대로 돌려준다")
    void noRace_runsOnce() {
        String result = support.retryOnCreateRace(() -> {
            calls.incrementAndGet();
            return "first";
        });

        assertThat(result).isEqualTo("first");
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("주문 그룹 유니크 제약에 걸리면 한 번 더 실행해 그 결과를 돌려준다")
    void orderGroupConstraint_retriesOnce() {
        String result = support.retryOnCreateRace(() -> failOnceWith(violationOf(Payment.UK_ORDER_GROUP)));

        assertThat(result).isEqualTo("second");
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("멱등키 유니크 제약에 걸려도 같은 경우로 보고 한 번 더 실행한다 (제약 이름의 대소문자는 가리지 않는다)")
    void idempotencyConstraint_retriesOnce() {
        String result = support.retryOnCreateRace(
                () -> failOnceWith(violationOf(Payment.UK_IDEMPOTENCY.toUpperCase())));

        assertThat(result).isEqualTo("second");
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("다른 제약 위반은 코드 버그이므로 다시 실행하지 않고 그대로 던진다")
    void otherConstraint_isNotRetried() {
        DataIntegrityViolationException failure = violationOf("uk_holding_order");

        assertThatThrownBy(() -> support.retryOnCreateRace(() -> failOnceWith(failure))).isSameAs(failure);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("어느 제약인지 알 수 없는 위반은 다시 실행하지 않고 그대로 던진다")
    void unknownViolation_isNotRetried() {
        DataIntegrityViolationException withoutCause = new DataIntegrityViolationException("not-null violation");
        DataIntegrityViolationException withoutName = violationOf(null);

        assertThatThrownBy(() -> support.retryOnCreateRace(() -> failOnceWith(withoutCause))).isSameAs(withoutCause);
        calls.set(0);
        assertThatThrownBy(() -> support.retryOnCreateRace(() -> failOnceWith(withoutName))).isSameAs(withoutName);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("다시 실행해도 실패하면 더 반복하지 않고 그 예외를 던진다")
    void secondFailure_isNotRetriedAgain() {
        DataIntegrityViolationException failure = violationOf(Payment.UK_ORDER_GROUP);

        assertThatThrownBy(() -> support.retryOnCreateRace(() -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(calls).hasValue(2);
    }
}
