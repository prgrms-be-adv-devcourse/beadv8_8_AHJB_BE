package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.back.boundedContext.delivery.out.deliveryTracker.DeliveryTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackerUseCaseTest {

    @Mock
    DeliveryTracker deliveryTracker;

    @InjectMocks
    DeliveryTrackerUseCase deliveryTrackerUseCase;

    @Test
    @DisplayName("Webhook 등록 이벤트를 받으면 이벤트의 배송 id와 주문 id를 순서대로 넘겨 Webhook을 2일 기한으로 등록한다")
    void registerWebhook_fromEvent() {
        deliveryTrackerUseCase.registerWebhook(new WebhookRegisterEvent(50L, 100L, CJ, "1234567890"));

        verify(deliveryTracker).registerWebhook(50L, 100L, CJ, "1234567890", 2);
    }
}
