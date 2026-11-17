package com.jjinmak.boundedcontext.delivery.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.delivery.domain.CarrierType;
import com.jjinmak.boundedcontext.delivery.app.event.CallbackRequestedEvent;
import com.jjinmak.boundedcontext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.boundedcontext.delivery.domain.Delivery;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedcontext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.boundedcontext.delivery.out.eventPublisher.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.stream.Stream;

import static com.jjinmak.boundedcontext.delivery.exception.DeliveryErrorCode.DELIVERY_NOT_FOUND;
import static com.jjinmak.common.exception.CommonErrorCode.BAD_REQUEST;
import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static com.jjinmak.contracts.delivery.domain.CarrierType.HANJIN;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackerUseCaseTest {

    @Mock
    DeliveryTracker deliveryTracker;

    @Mock
    DeliverySupport deliverySupport;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    DeliveryTrackerUseCase deliveryTrackerUseCase;

    Delivery delivery;
    String token;

    @BeforeEach
    void setUp() {
        delivery = new Delivery(100L, new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"), new DeliveryMember(2L, UUID.randomUUID(), "판매자"), CJ, "1234567890");
        ReflectionTestUtils.setField(delivery, "id", 50L);
        token = delivery.getCallbackToken().toString();
    }

    @Test
    @DisplayName("Webhook 등록 이벤트를 받으면 이벤트의 배송 id, 주문 id, 콜백 토큰을 순서대로 넘겨 Webhook을 2일 기한으로 등록한다")
    void registerWebhook_fromEvent() {
        UUID callbackToken = UUID.randomUUID();

        deliveryTrackerUseCase.registerWebhook(new WebhookRegisterEvent(50L, 100L, CJ, "1234567890", callbackToken));

        verify(deliveryTracker).registerWebhook(50L, 100L, CJ, "1234567890", callbackToken, 2);
    }

    @Test
    @DisplayName("Webhook 콜백의 토큰과 주문·택배사·운송장이 지금 배송과 맞으면 그 배송의 콜백 요청 이벤트를 발행한다")
    void webhookCallback_rightToken() {
        when(deliverySupport.getDeliveryById(50L)).thenReturn(delivery);

        deliveryTrackerUseCase.webhookCallback(50L, 100L, token, CJ, "1234567890");

        verify(eventPublisher).publish(new CallbackRequestedEvent(50L));
    }

    @ParameterizedTest(name = "토큰 \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"6f1c1b8e-0d0a-4c5e-9b7a-2f3d4e5f6a7b", "not-a-uuid"})
    @DisplayName("Webhook 콜백의 토큰이 없거나 발급한 토큰과 다르면 DELIVERY_NOT_FOUND, 콜백 요청 이벤트를 발행하지 않는다")
    void webhookCallback_missingOrWrongToken(String wrongToken) {
        when(deliverySupport.getDeliveryById(50L)).thenReturn(delivery);

        assertThatThrownBy(() -> deliveryTrackerUseCase.webhookCallback(50L, 100L, wrongToken, CJ, "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DELIVERY_NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("토큰이 없으면 택배사를 알 수 없어도(null) BAD_REQUEST가 아니라 DELIVERY_NOT_FOUND를 받는다")
    void webhookCallback_tokenCheckedBeforeCarrier() {
        when(deliverySupport.getDeliveryById(50L)).thenReturn(delivery);

        assertThatThrownBy(() -> deliveryTrackerUseCase.webhookCallback(50L, 100L, null, null, "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DELIVERY_NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("토큰이 맞아도 택배사를 알 수 없으면(null) BAD_REQUEST, 콜백 요청 이벤트를 발행하지 않는다")
    void webhookCallback_unknownCarrier() {
        when(deliverySupport.getDeliveryById(50L)).thenReturn(delivery);

        assertThatThrownBy(() -> deliveryTrackerUseCase.webhookCallback(50L, 100L, token, null, "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BAD_REQUEST);

        verifyNoInteractions(eventPublisher);
    }

    static Stream<Arguments> otherDeliveries() {
        return Stream.of(
                Arguments.of(101L, CJ, "1234567890"),
                Arguments.of(100L, HANJIN, "1234567890"),
                Arguments.of(100L, CJ, "9999999999")
        );
    }

    @ParameterizedTest(name = "주문 {0}, 택배사 {1}, 운송장 {2}")
    @MethodSource("otherDeliveries")
    @DisplayName("토큰이 맞아도 주문 id, 택배사, 운송장 중 하나가 지금 배송과 다르면 무시하고 콜백 요청 이벤트를 발행하지 않는다")
    void webhookCallback_otherDelivery(Long orderId, CarrierType carrier, String waybill) {
        when(deliverySupport.getDeliveryById(50L)).thenReturn(delivery);

        deliveryTrackerUseCase.webhookCallback(50L, orderId, token, carrier, waybill);

        verifyNoInteractions(eventPublisher);
    }
}
