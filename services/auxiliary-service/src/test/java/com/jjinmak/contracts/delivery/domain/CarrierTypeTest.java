package com.jjinmak.contracts.delivery.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierTypeTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(CarrierType.class)
    @DisplayName("Delivery Tracker 택배사 코드로 택배사를 찾는다")
    void fromCode_knownCode(CarrierType carrier) {
        assertThat(CarrierType.fromCode(carrier.getCarrierCode())).isEqualTo(carrier);
    }

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    @ValueSource(strings = {"kr.unknown", "CJ"})
    @DisplayName("Delivery Tracker 택배사 코드가 아니면(enum 이름 포함) 예외 없이 null을 돌려준다")
    void fromCode_unknownCode(String code) {
        assertThat(CarrierType.fromCode(code)).isNull();
    }
}
