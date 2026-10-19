package com.jjinmak.back.shared.delivery.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

class DeliveryStatusCodeTest {

    @ParameterizedTest(name = "{0}는 끝난 상태")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"})
    @DisplayName("배송 완료와 지정 장소 수령 가능은 배송이 끝난 상태다")
    void isCompleted_completedStatuses(DeliveryStatusCode code) {
        assertThat(code.isCompleted()).isTrue();
    }

    @ParameterizedTest(name = "{0}는 끝나지 않은 상태")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"}, mode = EXCLUDE)
    @DisplayName("그 밖의 상태는 EXCEPTION, UNKNOWN, WRONG을 포함해 배송이 끝난 상태가 아니다")
    void isCompleted_otherStatuses(DeliveryStatusCode code) {
        assertThat(code.isCompleted()).isFalse();
    }
}
