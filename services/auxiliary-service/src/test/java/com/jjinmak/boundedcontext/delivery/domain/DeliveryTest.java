package com.jjinmak.boundedcontext.delivery.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static com.jjinmak.contracts.delivery.domain.CarrierType.HANJIN;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {

    DeliveryMember winner = new DeliveryMember(1L, UUID.randomUUID(), "낙찰자");
    DeliveryMember seller = new DeliveryMember(2L, UUID.randomUUID(), "판매자");

    @Test
    @DisplayName("배송을 만들면 배송마다 다른 콜백 토큰을 발급하고, 발급한 토큰과 같은 값만 맞는다고 본다")
    void newDelivery_issuesDistinctCallbackToken() {
        Delivery first = new Delivery(100L, winner, seller, CJ, "1111");
        Delivery second = new Delivery(101L, winner, seller, CJ, "2222");

        assertThat(first.getCallbackToken()).isNotNull();
        assertThat(second.getCallbackToken()).isNotNull().isNotEqualTo(first.getCallbackToken());
        assertThat(first.matchesCallbackToken(first.getCallbackToken().toString())).isTrue();
        assertThat(first.matchesCallbackToken(second.getCallbackToken().toString())).isFalse();
    }

    @Test
    @DisplayName("택배사와 운송장을 바꾸면 콜백 토큰을 새로 발급해, 바꾸기 전 토큰은 더 이상 맞지 않는다")
    void update_reissuesCallbackToken() {
        Delivery delivery = new Delivery(100L, winner, seller, CJ, "1111");
        String tokenBefore = delivery.getCallbackToken().toString();

        delivery.update(HANJIN, "2222");

        assertThat(delivery.getCallbackToken().toString()).isNotEqualTo(tokenBefore);
        assertThat(delivery.matchesCallbackToken(tokenBefore)).isFalse();
        assertThat(delivery.matchesCallbackToken(delivery.getCallbackToken().toString())).isTrue();
    }

    @ParameterizedTest(name = "토큰 \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"6f1c1b8e-0d0a-4c5e-9b7a-2f3d4e5f6a7b", "not-a-uuid"})
    @DisplayName("콜백 토큰이 없거나 발급한 토큰과 다르면 맞지 않는다고 본다")
    void matchesCallbackToken_missingOrWrong(String token) {
        Delivery delivery = new Delivery(100L, winner, seller, CJ, "1111");

        assertThat(delivery.matchesCallbackToken(token)).isFalse();
    }
}
