package com.jjinmak.boundedcontext.delivery.in;

import com.jayway.jsonpath.JsonPath;
import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.boundedcontext.delivery.app.event.CallbackRequestedEvent;
import com.jjinmak.boundedcontext.delivery.app.event.WebhookRegisterEvent;
import com.jjinmak.boundedcontext.delivery.domain.Delivery;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedcontext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.boundedcontext.delivery.out.dto.DeliveryStatusResponseDto;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static com.jjinmak.contracts.delivery.domain.CarrierType.HANJIN;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.EXCEPTION;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@RecordApplicationEvents
class DeliveryApiControllerTest {

    @Autowired
    WebApplicationContext context;

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

    MockMvc mockMvc;

    DeliveryMember winner;
    DeliveryMember seller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new HeaderAuthenticationFilter())
                .build();

        winner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0} 배송 정보 수정")
    @EnumSource(value = DeliveryStatusCode.class, names = {"DELIVERED", "AVAILABLE_FOR_PICKUP"})
    @DisplayName("배송 정보 수정: 배송이 끝났으면 400 DELIVERY_BAD_REQUEST, 배송 정보가 그대로이고 Webhook 등록 이벤트를 발행하지 않는다")
    void updateDeliveryData_completed(DeliveryStatusCode status) throws Exception {
        Long deliveryId = saveDelivery(100L, status);

        mockMvc.perform(put("/api/v1/order/{orderId}/delivery", 100L)
                        .header(AuthHeaders.MEMBER_ID, 2L)
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
    @DisplayName("배송 정보 수정: 배송 문제(EXCEPTION)가 생긴 배송은 판매자가 운송장을 바꿀 수 있고, 상태가 WAITING이 되며 새로 발급한 콜백 토큰과 새 운송장으로 Webhook 등록 이벤트를 한 번 발행한다")
    void updateDeliveryData_exception() throws Exception {
        Long deliveryId = saveDelivery(100L, EXCEPTION);
        UUID tokenBefore = tokenOf(deliveryId);

        mockMvc.perform(put("/api/v1/order/{orderId}/delivery", 100L)
                        .header(AuthHeaders.MEMBER_ID, 2L)
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
        assertThat(delivery.getCallbackToken()).isNotEqualTo(tokenBefore);
        assertThat(events.stream(WebhookRegisterEvent.class))
                .containsExactly(new WebhookRegisterEvent(deliveryId, 100L, HANJIN, "NEW-222", delivery.getCallbackToken()));
    }

    @Test
    @DisplayName("배송 정보 수정: 낙찰자가 끝난 배송을 수정하면 403 DELIVERY_FORBIDDEN, 배송 정보가 그대로다")
    void updateDeliveryData_byWinner() throws Exception {
        Long deliveryId = saveDelivery(100L, DELIVERED);

        mockMvc.perform(put("/api/v1/order/{orderId}/delivery", 100L)
                        .header(AuthHeaders.MEMBER_ID, 1L)
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
                        .header(AuthHeaders.MEMBER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.carrier").value("CJ"))
                .andExpect(jsonPath("$.data.waybill").value("OLD-111"))
                .andExpect(jsonPath("$.data.code").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.message").value("이동 중"));
    }

    @Test
    @DisplayName("Webhook 콜백: Webhook을 등록할 때 넘긴 콜백 주소에는 배송의 콜백 토큰이 쿼리로 붙고, 그 주소로 콜백이 오면 202를 돌려주며 그 배송의 콜백 요청 이벤트를 발행한다")
    void webhookCallback_toRegisteredCallbackUrl() throws Exception {
        Long deliveryId = saveDelivery(100L, IN_TRANSIT);
        UUID token = tokenOf(deliveryId);
        String registerRequest = registerWebhook(deliveryId, 100L, token);
        String callbackUrl = JsonPath.read(registerRequest, "$.variables.input.callbackUrl");
        String carrierId = JsonPath.read(registerRequest, "$.variables.input.carrierId");
        String trackingNumber = JsonPath.read(registerRequest, "$.variables.input.trackingNumber");

        assertThat(callbackUrl).isEqualTo("https://api.jjinmak.kro.kr/api/v1/order/100/delivery/" + deliveryId + "?token=" + token);

        mockMvc.perform(post(URI.create(callbackUrl))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"%s\", \"trackingNumber\": \"%s\"}".formatted(carrierId, trackingNumber)))
                .andExpect(status().isAccepted());

        assertThat(events.stream(CallbackRequestedEvent.class))
                .containsExactly(new CallbackRequestedEvent(deliveryId));
    }

    @ParameterizedTest(name = "쿼리 \"{0}\"")
    @ValueSource(strings = {"", "?token=", "?token=6f1c1b8e-0d0a-4c5e-9b7a-2f3d4e5f6a7b", "?token=not-a-uuid"})
    @DisplayName("Webhook 콜백: 콜백 토큰이 없거나 다르면 404 DELIVERY_002, 콜백 요청 이벤트를 발행하지 않는다")
    void webhookCallback_missingOrWrongToken(String query) throws Exception {
        Long deliveryId = saveDelivery(100L, IN_TRANSIT);

        mockMvc.perform(post(URI.create("/api/v1/order/100/delivery/" + deliveryId + query))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"kr.cjlogistics\", \"trackingNumber\": \"OLD-111\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("DELIVERY_002"));

        assertThat(events.stream(CallbackRequestedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("Webhook 콜백: 토큰이 없으면 모르는 택배사 코드로 와도 400이 아니라 404 DELIVERY_002를 돌려준다")
    void webhookCallback_withoutTokenAndUnknownCarrier() throws Exception {
        Long deliveryId = saveDelivery(100L, IN_TRANSIT);

        mockMvc.perform(post("/api/v1/order/{orderId}/delivery/{deliveryId}", 100L, deliveryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"kr.unknown\", \"trackingNumber\": \"OLD-111\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DELIVERY_002"));

        assertThat(events.stream(CallbackRequestedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("Webhook 콜백: 토큰이 맞아도 모르는 택배사 코드로 콜백이 오면 400 BAD_REQUEST, 콜백 요청 이벤트를 발행하지 않는다")
    void webhookCallback_unknownCarrier() throws Exception {
        Long deliveryId = saveDelivery(100L, IN_TRANSIT);

        mockMvc.perform(post("/api/v1/order/{orderId}/delivery/{deliveryId}", 100L, deliveryId)
                        .queryParam("token", tokenOf(deliveryId).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"kr.unknown\", \"trackingNumber\": \"OLD-111\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        assertThat(events.stream(CallbackRequestedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("Webhook 콜백: 판매자가 운송장을 바꾸면 바꾸기 전 토큰의 콜백은 404 DELIVERY_002를 받고, 새 토큰의 콜백만 202와 함께 콜백 요청 이벤트를 발행한다")
    void webhookCallback_oldTokenAfterWaybillChange() throws Exception {
        Long deliveryId = saveDelivery(100L, EXCEPTION);
        UUID oldToken = tokenOf(deliveryId);

        mockMvc.perform(put("/api/v1/order/{orderId}/delivery", 100L)
                        .header(AuthHeaders.MEMBER_ID, 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"HANJIN\", \"waybill\": \"NEW-222\"}"))
                .andExpect(status().isOk());
        UUID newToken = tokenOf(deliveryId);

        mockMvc.perform(post("/api/v1/order/{orderId}/delivery/{deliveryId}", 100L, deliveryId)
                        .queryParam("token", oldToken.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"kr.hanjin\", \"trackingNumber\": \"NEW-222\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DELIVERY_002"));
        assertThat(events.stream(CallbackRequestedEvent.class)).isEmpty();

        mockMvc.perform(post("/api/v1/order/{orderId}/delivery/{deliveryId}", 100L, deliveryId)
                        .queryParam("token", newToken.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"kr.hanjin\", \"trackingNumber\": \"NEW-222\"}"))
                .andExpect(status().isAccepted());
        assertThat(events.stream(CallbackRequestedEvent.class))
                .containsExactly(new CallbackRequestedEvent(deliveryId));
    }

    private Long saveDelivery(Long orderId, DeliveryStatusCode status) {
        Delivery delivery = new Delivery(orderId, winner, seller, CJ, "OLD-111");
        delivery.updateStatus(status);
        return deliveryRepository.save(delivery).getId();
    }

    private UUID tokenOf(Long deliveryId) {
        return deliveryRepository.findById(deliveryId).orElseThrow().getCallbackToken();
    }

    private String registerWebhook(Long deliveryId, Long orderId, UUID token) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://apis.tracker.delivery/graphql");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.expect(request -> requestBody.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"data\": {\"registerTrackWebhook\": true}}", MediaType.APPLICATION_JSON));

        new DeliveryTracker(builder.build()).registerWebhook(deliveryId, orderId, CJ, "OLD-111", token, 2);

        server.verify();
        return requestBody.get();
    }
}
