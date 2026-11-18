package com.jjinmak.back.payment.app.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 요청 · 결과 계약(libs)에 결제수단 · 포인트 필드를 넣은 뒤에도 필드를 모르는 기존 호출부가 그대로 동작하는지.
 */
class PaymentContractCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("결제수단 · 포인트를 넘기지 않는 기존 생성 방식은 예치금 결제(포인트 0) 요청이 된다")
    void legacyConstructor() {
        PaymentRequestDto request = new PaymentRequestDto(50L, 1L, 33_000L,
                List.of(new PaymentRequestDto.Item(1_001L, 2L, 30_000L, 3_000L)));

        assertThat(request.payMethod()).isNull();
        assertThat(request.pointUseAmount()).isZero();
        assertThat(request).isEqualTo(new PaymentRequestDto(50L, 1L, 33_000L,
                List.of(new PaymentRequestDto.Item(1_001L, 2L, 30_000L, 3_000L)), null, 0L));
    }

    @Test
    @DisplayName("결제수단 · 포인트 필드가 없는 JSON 요청도 읽을 수 있고, 예치금 결제(포인트 0)로 읽힌다")
    void jsonWithoutNewFields() throws Exception {
        String json = """
                {"orderGroupId":50,"buyerId":1,"totalPrice":33000,
                 "items":[{"orderId":1001,"sellerId":2,"winningPrice":30000,"deliveryFee":3000}]}
                """;

        PaymentRequestDto request = objectMapper.readValue(json, PaymentRequestDto.class);

        assertThat(request.totalPrice()).isEqualTo(33_000L);
        assertThat(request.items()).hasSize(1);
        assertThat(request.payMethod()).isNull();
        assertThat(request.pointUseAmount()).isZero();
    }

    @Test
    @DisplayName("결제수단 · 포인트 필드가 있는 JSON 요청은 그 값으로 읽힌다")
    void jsonWithNewFields() throws Exception {
        String json = """
                {"orderGroupId":50,"buyerId":1,"totalPrice":33000,
                 "items":[{"orderId":1001,"sellerId":2,"winningPrice":30000,"deliveryFee":3000}],
                 "payMethod":"CARD","pointUseAmount":3000}
                """;

        PaymentRequestDto request = objectMapper.readValue(json, PaymentRequestDto.class);

        assertThat(request.payMethod()).isEqualTo("CARD");
        assertThat(request.pointUseAmount()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("기존 결과 생성 방식(paid, 인자 5개 · 7개 needsCharge)은 전액 예치금 결제로 채워진다")
    void legacyResultFactories() {
        PaymentResultDto paid = PaymentResultDto.paid(7L, 33_000L);
        assertThat(paid.isPaid()).isTrue();
        assertThat(paid.payMethod()).isEqualTo("DEPOSIT");
        assertThat(paid.depositAmount()).isEqualTo(33_000L);
        assertThat(paid.pointAmount()).isZero();
        assertThat(paid.pgAmount()).isZero();

        PaymentResultDto needsCharge = PaymentResultDto.needsCharge(7L, 33_000L, 13_000L, "CHG-1", "주문 결제");
        assertThat(needsCharge.status()).isEqualTo(PaymentResultDto.Status.NEEDS_CHARGE);
        assertThat(needsCharge.shortageAmount()).isEqualTo(13_000L);
        assertThat(needsCharge.depositAmount()).isEqualTo(33_000L);
        assertThat(needsCharge.needsPgWindow()).isTrue();
    }

    @Test
    @DisplayName("직접 결제 결과는 결제창 금액과 직접 결제 금액이 같고, 남는 돈이 없다")
    void needsPaymentResult() {
        PaymentResultDto result = PaymentResultDto.needsPayment(7L, 33_000L, "CHG-1", "찐막 주문 결제", "CARD",
                new PaymentResultDto.Breakdown("CARD", 3_000L, 0L, 30_000L));

        assertThat(result.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        assertThat(result.isPaid()).isFalse();
        assertThat(result.chargeAmount()).isEqualTo(30_000L);
        assertThat(result.shortageAmount()).isEqualTo(30_000L);
        assertThat(result.pgMethod()).isEqualTo("CARD");
        assertThat(result.pointAmount()).isEqualTo(3_000L);
    }
}
