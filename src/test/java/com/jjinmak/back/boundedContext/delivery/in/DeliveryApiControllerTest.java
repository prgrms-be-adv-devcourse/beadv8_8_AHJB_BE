package com.jjinmak.back.boundedContext.delivery.in;

import com.jjinmak.back.boundedContext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.back.boundedContext.delivery.domain.Delivery;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import com.jjinmak.back.boundedContext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.back.boundedContext.delivery.out.dto.DeliveryStatusResponseDto;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.back.boundedContext.delivery.out.repository.DeliveryRepository;
import com.jjinmak.back.global.security.JwtTokenProvider;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static com.jjinmak.back.shared.delivery.domain.CarrierType.HANJIN;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.EXCEPTION;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class DeliveryApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    DeliveryMemberRepository deliveryMemberRepository;

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    ApplicationEvents events;

    @MockitoBean
    DeliveryTracker deliveryTracker;

    @MockitoBean
    DeliveryScheduler deliveryScheduler;

    DeliveryMember winner;
    DeliveryMember seller;

    @BeforeEach
    void setUp() {
        winner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
    }

    @ParameterizedTest(name = "{0} 배송 정보 수정")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"})
    @DisplayName("배송 정보 수정: 배송이 끝났으면 400 DELIVERY_BAD_REQUEST, 배송 정보가 그대로이고 Webhook 등록 이벤트를 발행하지 않는다")
    void updateDeliveryData_completed(DeliveryStatusCode status) throws Exception {
        Long deliveryId = saveDelivery(100L, status);

        mockMvc.perform(put("/api/v1/order/{orderId}/delivery", 100L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"HANJIN\", \"waybill\": \"NEW-222\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("DELIVERY_001"));

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getCarrier()).isEqualTo(CJ);
        assertThat(delivery.getWaybill()).isEqualTo("OLD-111");
        assertThat(delivery.getStatus()).isEqualTo(status);
        assertThat(events.stream(WebhookRegisterEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("배송 정보 수정: 배송 문제(EXCEPTION)가 생긴 배송은 판매자가 운송장을 바꿀 수 있고, 상태가 WAITING이 되며 새 운송장으로 Webhook 등록 이벤트를 한 번 발행한다")
    void updateDeliveryData_exception() throws Exception {
        Long deliveryId = saveDelivery(100L, EXCEPTION);

        mockMvc.perform(put("/api/v1/order/{orderId}/delivery", 100L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"HANJIN\", \"waybill\": \"NEW-222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.carrier").value("HANJIN"))
                .andExpect(jsonPath("$.data.waybill").value("NEW-222"));

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getCarrier()).isEqualTo(HANJIN);
        assertThat(delivery.getWaybill()).isEqualTo("NEW-222");
        assertThat(delivery.getStatus()).isEqualTo(WAITING);
        assertThat(events.stream(WebhookRegisterEvent.class))
                .containsExactly(new WebhookRegisterEvent(deliveryId, 100L, HANJIN, "NEW-222"));
    }

    @Test
    @DisplayName("배송 정보 수정: 낙찰자가 끝난 배송을 수정하면 403 DELIVERY_FORBIDDEN, 배송 정보가 그대로다")
    void updateDeliveryData_byWinner() throws Exception {
        Long deliveryId = saveDelivery(100L, DELIVERED);

        mockMvc.perform(put("/api/v1/order/{orderId}/delivery", 100L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"HANJIN\", \"waybill\": \"NEW-222\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DELIVERY_003"));

        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getWaybill()).isEqualTo("OLD-111");
        assertThat(events.stream(WebhookRegisterEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("배송 현황 조회: 마지막 배송 상태와 함께 등록된 택배사와 운송장 번호를 돌려준다")
    void getDeliveryStatus_withCarrierAndWaybill() throws Exception {
        saveDelivery(100L, IN_TRANSIT);
        when(deliveryTracker.trackLastStatus(CJ, "OLD-111")).thenReturn(
                new DeliveryStatusResponseDto(
                        new DeliveryStatusResponseDto.Data(
                                new DeliveryStatusResponseDto.Track(
                                        new DeliveryStatusResponseDto.LastEvent(
                                                OffsetDateTime.of(2026, 10, 19, 15, 0, 0, 0, ZoneOffset.ofHours(9)),
                                                new DeliveryStatusResponseDto.Status(IN_TRANSIT)
                                        )
                                )
                        ),
                        null
                )
        );

        mockMvc.perform(get("/api/v1/order/{orderId}/delivery", 100L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.carrier").value("CJ"))
                .andExpect(jsonPath("$.data.waybill").value("OLD-111"))
                .andExpect(jsonPath("$.data.code").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.message").value("이동 중"));
    }

    private Long saveDelivery(Long orderId, DeliveryStatusCode status) {
        Delivery delivery = new Delivery(orderId, winner, seller, CJ, "OLD-111");
        delivery.updateStatus(status);
        return deliveryRepository.save(delivery).getId();
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }
}
