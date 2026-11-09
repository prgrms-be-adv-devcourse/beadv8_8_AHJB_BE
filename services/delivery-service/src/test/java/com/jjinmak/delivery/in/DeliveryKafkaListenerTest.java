package com.jjinmak.delivery.in;

import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.delivery.app.DeliveryFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.UUID;
import java.util.stream.Stream;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryKafkaListenerTest {

    @Mock
    DeliveryFacade deliveryFacade;

    @InjectMocks
    DeliveryKafkaListener deliveryKafkaListener;

    static Stream<Arguments> subscriptions() {
        return Stream.of(
                arguments(MemberCreatedEvent.class, "member.created"),
                arguments(OrderSendProductEvent.class, "order.send-product")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("subscriptions")
    @DisplayName("이벤트마다 그 이벤트의 토픽 하나만 delivery 그룹으로 구독한다")
    void handle_subscribesTopicWithDeliveryGroup(Class<?> eventType, String topic) throws NoSuchMethodException {
        KafkaListener listener = DeliveryKafkaListener.class.getMethod("handle", eventType).getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly(topic);
        assertThat(listener.groupId()).isEqualTo("delivery");
    }

    @Test
    @DisplayName("회원 생성 이벤트를 받으면 그 회원 정보로 동기화한다")
    void memberCreated_syncsMember() {
        MemberDto member = new MemberDto(2L, UUID.randomUUID(), "판매자");

        deliveryKafkaListener.handle(new MemberCreatedEvent(member));

        verify(deliveryFacade).syncMember(member);
    }

    @Test
    @DisplayName("상품 발송 이벤트를 받으면 그 이벤트로 배송을 만든다")
    void sendProduct_createsDelivery() {
        OrderSendProductEvent event = new OrderSendProductEvent(100L, 1L, 2L, CJ, "1234567890");

        deliveryKafkaListener.handle(event);

        verify(deliveryFacade).createDelivery(event);
    }
}
