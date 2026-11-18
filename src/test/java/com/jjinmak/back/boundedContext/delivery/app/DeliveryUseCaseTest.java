package com.jjinmak.back.boundedContext.delivery.app;

import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryResponseDto;
import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.delivery.exception.DeliveryErrorCode.DELIVERY_BAD_REQUEST;
import static com.jjinmak.back.boundedContext.delivery.exception.DeliveryErrorCode.DELIVERY_FORBIDDEN;
import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static com.jjinmak.back.shared.delivery.domain.CarrierType.HANJIN;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryUseCaseTest {

    @Mock
    DeliverySupport deliverySupport;

    @Mock
    DeliveryRepository deliveryRepository;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    DeliveryUseCase deliveryUseCase;

    DeliveryMember winner;
    DeliveryMember seller;

    @BeforeEach
    void setUp() {
        winner = new DeliveryMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new DeliveryMember(2L, UUID.randomUUID(), "판매자");
    }

    @ParameterizedTest(name = "{0} 배송 정보 수정")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"})
    @DisplayName("배송이 끝난 뒤 판매자가 배송 정보를 바꾸면 DELIVERY_BAD_REQUEST, 택배사·운송장·상태가 그대로이고 Webhook 등록 이벤트를 발행하지 않는다")
    void updateDeliveryData_completed(DeliveryStatusCode status) {
        Delivery delivery = delivery(status);
        when(deliverySupport.getDeliveryWithParticipant(100L)).thenReturn(delivery);
        when(deliverySupport.getMember(2L)).thenReturn(seller);

        assertThatThrownBy(() -> deliveryUseCase.updateDeliveryData(2L, 100L, HANJIN, "NEW-222"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DELIVERY_BAD_REQUEST);

        assertThat(delivery.getCarrier()).isEqualTo(CJ);
        assertThat(delivery.getWaybill()).isEqualTo("OLD-111");
        assertThat(delivery.getStatus()).isEqualTo(status);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest(name = "{0} 배송 정보 수정")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"}, mode = EXCLUDE)
    @DisplayName("배송이 끝나기 전에는 EXCEPTION을 포함해 판매자가 배송 정보를 바꿀 수 있고, 상태가 WAITING이 되며 새 운송장으로 Webhook 등록 이벤트를 발행한다")
    void updateDeliveryData_notCompleted(DeliveryStatusCode status) {
        Delivery delivery = delivery(status);
        when(deliverySupport.getDeliveryWithParticipant(100L)).thenReturn(delivery);
        when(deliverySupport.getMember(2L)).thenReturn(seller);

        DeliveryResponseDto response = deliveryUseCase.updateDeliveryData(2L, 100L, HANJIN, "NEW-222");

        assertThat(response).isEqualTo(new DeliveryResponseDto(100L, HANJIN, "NEW-222"));
        assertThat(delivery.getCarrier()).isEqualTo(HANJIN);
        assertThat(delivery.getWaybill()).isEqualTo("NEW-222");
        assertThat(delivery.getStatus()).isEqualTo(WAITING);
        verify(eventPublisher).publish(new WebhookRegisterEvent(50L, 100L, HANJIN, "NEW-222"));
    }

    @Test
    @DisplayName("판매자가 아닌 회원은 배송이 끝났어도 DELIVERY_FORBIDDEN을 먼저 받고, 배송 정보가 그대로다")
    void updateDeliveryData_notSellerOnCompleted() {
        Delivery delivery = delivery(DELIVERED);
        when(deliverySupport.getDeliveryWithParticipant(100L)).thenReturn(delivery);
        when(deliverySupport.getMember(1L)).thenReturn(winner);

        assertThatThrownBy(() -> deliveryUseCase.updateDeliveryData(1L, 100L, HANJIN, "NEW-222"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DELIVERY_FORBIDDEN);

        assertThat(delivery.getWaybill()).isEqualTo("OLD-111");
        assertThat(delivery.getStatus()).isEqualTo(DELIVERED);
        verifyNoInteractions(eventPublisher);
    }

    private Delivery delivery(DeliveryStatusCode status) {
        Delivery delivery = new Delivery(100L, winner, seller, CJ, "OLD-111");
        delivery.updateStatus(status);
        ReflectionTestUtils.setField(delivery, "id", 50L);
        return delivery;
    }
}
