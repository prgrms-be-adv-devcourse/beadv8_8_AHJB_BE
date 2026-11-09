package com.jjinmak.delivery.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.delivery.out.eventPublisher.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.jjinmak.common.exception.CommonErrorCode.BAD_REQUEST;
import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Test
    @DisplayName("Webhook 등록 이벤트를 받으면 이벤트의 배송 id와 주문 id를 순서대로 넘겨 Webhook을 2일 기한으로 등록한다")
    void registerWebhook_fromEvent() {
        deliveryTrackerUseCase.registerWebhook(new WebhookRegisterEvent(50L, 100L, CJ, "1234567890"));

        verify(deliveryTracker).registerWebhook(50L, 100L, CJ, "1234567890", 2);
    }

    @Test
    @DisplayName("Webhook 콜백의 택배사를 알 수 없으면(null) BAD_REQUEST, 배송을 조회하지 않고 콜백 요청 이벤트를 발행하지 않는다")
    void webhookCallback_unknownCarrier() {
        assertThatThrownBy(() -> deliveryTrackerUseCase.webhookCallback(50L, 100L, null, "1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BAD_REQUEST);

        verifyNoInteractions(deliverySupport, eventPublisher);
    }
}
