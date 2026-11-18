package com.jjinmak.boundedcontext.delivery.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.boundedcontext.delivery.app.dto.DeliveryResponseDto;
import com.jjinmak.boundedcontext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.boundedcontext.delivery.domain.Delivery;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedcontext.delivery.out.eventPublisher.EventPublisher;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static com.jjinmak.contracts.delivery.domain.CarrierType.HANJIN;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.WAITING;
import static com.jjinmak.boundedcontext.delivery.exception.DeliveryErrorCode.DELIVERY_BAD_REQUEST;
import static com.jjinmak.boundedcontext.delivery.exception.DeliveryErrorCode.DELIVERY_FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
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

    @Captor
    ArgumentCaptor<Delivery> deliveryCaptor;

    DeliveryMember winner;
    DeliveryMember seller;

    @BeforeEach
    void setUp() {
        winner = new DeliveryMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new DeliveryMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("발송 요청으로 배송을 만들면 콜백 토큰을 발급하고, 배송 생성 이벤트와 그 토큰을 담은 Webhook 등록 이벤트를 발행한다")
    void createDelivery_issuesCallbackToken() {
        when(deliverySupport.getMember(1L)).thenReturn(winner);
        when(deliverySupport.getMember(2L)).thenReturn(seller);
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(invocation -> {
            Delivery saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 50L);
            return saved;
        });

        deliveryUseCase.createDelivery(new OrderSendProductEvent(100L, 1L, 2L, CJ, "NEW-111"));

        verify(deliveryRepository).save(deliveryCaptor.capture());
        UUID token = deliveryCaptor.getValue().getCallbackToken();
        assertThat(token).isNotNull();
        verify(eventPublisher).publish(new DeliveryCreatedEvent(100L));
        verify(eventPublisher).publish(new WebhookRegisterEvent(50L, 100L, CJ, "NEW-111", token));
    }

    @ParameterizedTest(name = "{0} 배송 정보 수정")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"})
    @DisplayName("배송이 끝난 뒤 판매자가 배송 정보를 바꾸면 DELIVERY_BAD_REQUEST, 택배사·운송장·상태·콜백 토큰이 그대로이고 Webhook 등록 이벤트를 발행하지 않는다")
    void updateDeliveryData_completed(DeliveryStatusCode status) {
        Delivery delivery = delivery(status);
        UUID tokenBefore = delivery.getCallbackToken();
        when(deliverySupport.getDeliveryWithParticipant(100L)).thenReturn(delivery);
        when(deliverySupport.getMember(2L)).thenReturn(seller);

        assertThatThrownBy(() -> deliveryUseCase.updateDeliveryData(2L, 100L, HANJIN, "NEW-222"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DELIVERY_BAD_REQUEST);

        assertThat(delivery.getCarrier()).isEqualTo(CJ);
        assertThat(delivery.getWaybill()).isEqualTo("OLD-111");
        assertThat(delivery.getStatus()).isEqualTo(status);
        assertThat(delivery.getCallbackToken()).isEqualTo(tokenBefore);
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest(name = "{0} 배송 정보 수정")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"}, mode = EXCLUDE)
    @DisplayName("배송이 끝나기 전에는 EXCEPTION을 포함해 판매자가 배송 정보를 바꿀 수 있고, 상태가 WAITING이 되며 콜백 토큰을 새로 발급해 새 운송장과 함께 Webhook 등록 이벤트를 발행한다")
    void updateDeliveryData_notCompleted(DeliveryStatusCode status) {
        Delivery delivery = delivery(status);
        UUID tokenBefore = delivery.getCallbackToken();
        when(deliverySupport.getDeliveryWithParticipant(100L)).thenReturn(delivery);
        when(deliverySupport.getMember(2L)).thenReturn(seller);

        DeliveryResponseDto response = deliveryUseCase.updateDeliveryData(2L, 100L, HANJIN, "NEW-222");

        assertThat(response).isEqualTo(new DeliveryResponseDto(100L, HANJIN, "NEW-222"));
        assertThat(delivery.getCarrier()).isEqualTo(HANJIN);
        assertThat(delivery.getWaybill()).isEqualTo("NEW-222");
        assertThat(delivery.getStatus()).isEqualTo(WAITING);
        assertThat(delivery.getCallbackToken()).isNotNull().isNotEqualTo(tokenBefore);
        verify(eventPublisher).publish(new WebhookRegisterEvent(50L, 100L, HANJIN, "NEW-222", delivery.getCallbackToken()));
    }

    @Test
    @DisplayName("판매자가 아닌 회원은 배송이 끝났어도 DELIVERY_FORBIDDEN을 먼저 받고, 배송 정보와 콜백 토큰이 그대로다")
    void updateDeliveryData_notSellerOnCompleted() {
        Delivery delivery = delivery(DELIVERED);
        UUID tokenBefore = delivery.getCallbackToken();
        when(deliverySupport.getDeliveryWithParticipant(100L)).thenReturn(delivery);
        when(deliverySupport.getMember(1L)).thenReturn(winner);

        assertThatThrownBy(() -> deliveryUseCase.updateDeliveryData(1L, 100L, HANJIN, "NEW-222"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DELIVERY_FORBIDDEN);

        assertThat(delivery.getWaybill()).isEqualTo("OLD-111");
        assertThat(delivery.getStatus()).isEqualTo(DELIVERED);
        assertThat(delivery.getCallbackToken()).isEqualTo(tokenBefore);
        verifyNoInteractions(eventPublisher);
    }

    private Delivery delivery(DeliveryStatusCode status) {
        Delivery delivery = new Delivery(100L, winner, seller, CJ, "OLD-111");
        delivery.updateStatus(status);
        ReflectionTestUtils.setField(delivery, "id", 50L);
        return delivery;
    }
}
