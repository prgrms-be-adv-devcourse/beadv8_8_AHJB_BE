package com.jjinmak.boundedcontext.delivery.config;

import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.boundedcontext.delivery.domain.Delivery;
import com.jjinmak.boundedcontext.delivery.domain.DeliveryMember;
import com.jjinmak.boundedcontext.delivery.in.DeliveryScheduler;
import com.jjinmak.boundedcontext.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.boundedcontext.delivery.out.repository.DeliveryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DeliveryMemberRepository deliveryMemberRepository;

    @Autowired
    DeliveryRepository deliveryRepository;

    @MockitoBean
    DeliveryTracker deliveryTracker;

    @MockitoBean
    DeliveryScheduler deliveryScheduler;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "PUT"})
    @DisplayName("배송 조회와 배송 정보 수정은 회원 헤더가 없으면 401 COMMON002를 돌려준다")
    void deliveryApis_withoutMemberHeader_returnUnauthorized(String method) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), "/api/v1/order/{orderId}/delivery", 100L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @Test
    @DisplayName("회원 헤더가 있으면 인증을 통과해 배송 조회까지 진행한다 (배송이 없으면 404 DELIVERY_002)")
    void deliveryStatus_withMemberHeader_passesAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/order/{orderId}/delivery", 100L)
                        .header(AuthHeaders.MEMBER_ID, 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DELIVERY_002"));
    }

    @Test
    @DisplayName("Delivery Tracker의 Webhook 콜백은 회원 헤더 없이도 콜백 토큰이 맞으면 받아 202를 돌려준다")
    void webhookCallback_withoutMemberHeader_isAccepted() throws Exception {
        DeliveryMember winner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        DeliveryMember seller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
        Delivery delivery = deliveryRepository.save(new Delivery(100L, winner, seller, CJ, "WB-111"));

        mockMvc.perform(post("/api/v1/order/{orderId}/delivery/{deliveryId}", 100L, delivery.getId())
                        .queryParam("token", delivery.getCallbackToken().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"kr.cjlogistics\", \"trackingNumber\": \"WB-111\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    @DisplayName("Webhook 콜백 경로는 POST만 열려 있어, 다른 메서드는 회원 헤더가 없으면 401 COMMON002를 돌려준다")
    void webhookPath_otherMethodsWithoutMemberHeader_returnUnauthorized(String method) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), "/api/v1/order/{orderId}/delivery/{deliveryId}", 100L, 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1/order/100/delivery", "/api/v1/order/100/delivery/1/extra"})
    @DisplayName("인증 없이 POST로 열린 것은 Webhook 콜백 경로뿐이라, 다른 배송 경로의 POST는 회원 헤더가 없으면 401 COMMON002를 돌려준다")
    void otherDeliveryPaths_postWithoutMemberHeader_returnUnauthorized(String uri) throws Exception {
        mockMvc.perform(post(uri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness", "/v3/api-docs", "/swagger-ui/index.html"})
    @DisplayName("헬스 체크와 API 문서는 회원 헤더 없이 열린다")
    void publicUrls_withoutMemberHeader_areOpen(String uri) throws Exception {
        mockMvc.perform(get(uri))
                .andExpect(status().isOk());
    }
}
