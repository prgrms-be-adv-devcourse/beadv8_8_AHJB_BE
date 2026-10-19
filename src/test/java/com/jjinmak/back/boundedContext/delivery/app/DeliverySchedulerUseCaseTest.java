package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.dto.WebhookTargetDto;
import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryRepository;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverySchedulerUseCaseTest {

    @Mock
    DeliveryTracker deliveryTracker;

    @Mock
    DeliveryRepository deliveryRepository;

    @InjectMocks
    DeliverySchedulerUseCase deliverySchedulerUseCase;

    @Captor
    ArgumentCaptor<Collection<DeliveryStatusCode>> statusCaptor;

    WebhookTargetDto target = new WebhookTargetDto(50L, 100L, CJ, "1234567890");

    @ParameterizedTest(name = "최신 배송 상태 {0}")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"})
    @DisplayName("최신 배송 상태가 배송이 끝난 상태면 Webhook을 다시 등록하지 않는다")
    void updateWebhook_completed(DeliveryStatusCode latestStatus) {
        deliverySchedulerUseCase.updateWebhook(target, latestStatus);

        verifyNoInteractions(deliveryTracker);
    }

    @ParameterizedTest(name = "최신 배송 상태 {0}")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"}, mode = EXCLUDE)
    @DisplayName("최신 배송 상태가 EXCEPTION을 포함해 배송이 끝나지 않은 상태면 같은 배송으로 Webhook을 2일 기한으로 다시 등록한다")
    void updateWebhook_notCompleted(DeliveryStatusCode latestStatus) {
        deliverySchedulerUseCase.updateWebhook(target, latestStatus);

        verify(deliveryTracker).registerWebhook(50L, 100L, CJ, "1234567890", 2);
    }

    @Test
    @DisplayName("최신 배송 상태를 알지 못하면(null) Webhook을 다시 등록한다")
    void updateWebhook_latestStatusUnknown() {
        deliverySchedulerUseCase.updateWebhook(target, null);

        verify(deliveryTracker).registerWebhook(50L, 100L, CJ, "1234567890", 2);
    }

    @Test
    @DisplayName("재등록 대상은 배송이 끝나지 않은 모든 상태의 배송이고, 배송 id·주문 id·택배사·운송장을 담아 돌려준다")
    void getUnshippedDelivery_notCompletedStatuses() {
        Delivery delivery = new Delivery(100L, new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"), new DeliveryMember(2L, UUID.randomUUID(), "판매자"), CJ, "1234567890");
        ReflectionTestUtils.setField(delivery, "id", 50L);
        when(deliveryRepository.findAllByDeliveryStatusIn(anyCollection())).thenReturn(List.of(delivery));

        List<WebhookTargetDto> targets = deliverySchedulerUseCase.getUnshippedDelivery();

        verify(deliveryRepository).findAllByDeliveryStatusIn(statusCaptor.capture());
        assertThat(statusCaptor.getValue()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(DeliveryStatusCode.values()).filter(code -> !code.isCompleted()).toList()
        );
        assertThat(targets).containsExactly(target);
    }
}
