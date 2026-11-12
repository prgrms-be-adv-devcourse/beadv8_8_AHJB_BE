package com.jjinmak.delivery.config;

import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.delivery.domain.Delivery;
import com.jjinmak.delivery.domain.DeliveryMember;
import com.jjinmak.delivery.in.DeliveryScheduler;
import com.jjinmak.delivery.out.deliveryTracker.DeliveryTracker;
import com.jjinmak.delivery.out.repository.DeliveryMemberRepository;
import com.jjinmak.delivery.out.repository.DeliveryRepository;
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
    @DisplayName("Delivery Tracker의 Webhook 콜백은 회원 헤더 없이도 받아 202를 돌려준다")
    void webhookCallback_withoutMemberHeader_isAccepted() throws Exception {
        DeliveryMember winner = deliveryMemberRepository.save(new DeliveryMember(1L, UUID.randomUUID(), "낙찰자"));
        DeliveryMember seller = deliveryMemberRepository.save(new DeliveryMember(2L, UUID.randomUUID(), "판매자"));
        Long deliveryId = deliveryRepository.save(new Delivery(100L, winner, seller, CJ, "WB-111")).getId();

        mockMvc.perform(post("/api/v1/order/{orderId}/delivery/{deliveryId}", 100L, deliveryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrierId\": \"kr.cjlogistics\", \"trackingNumber\": \"WB-111\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness", "/v3/api-docs", "/swagger-ui/index.html"})
    @DisplayName("헬스 체크와 API 문서는 회원 헤더 없이 열린다")
    void publicUrls_withoutMemberHeader_areOpen(String uri) throws Exception {
        mockMvc.perform(get(uri))
                .andExpect(status().isOk());
    }
}
