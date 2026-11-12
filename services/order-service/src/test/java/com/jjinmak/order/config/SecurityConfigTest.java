package com.jjinmak.order.config;

import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.out.repository.OrderMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    OrderMemberRepository orderMemberRepository;

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("memberApis")
    @DisplayName("장바구니와 주문 API는 회원 헤더가 없으면 401 COMMON002를 돌려준다")
    void memberApis_withoutMemberHeader_returnUnauthorized(HttpMethod method, String uri) throws Exception {
        mockMvc.perform(request(method, uri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @Test
    @DisplayName("회원 헤더가 있으면 인증을 통과해 장바구니를 조회한다")
    void cart_withMemberHeader_passesAuthentication() throws Exception {
        orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));

        mockMvc.perform(get("/api/v1/cart")
                        .header(AuthHeaders.MEMBER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness", "/v3/api-docs", "/swagger-ui/index.html"})
    @DisplayName("헬스 체크와 API 문서는 회원 헤더 없이 열린다")
    void publicUrls_withoutMemberHeader_areOpen(String uri) throws Exception {
        mockMvc.perform(get(uri))
                .andExpect(status().isOk());
    }

    static Stream<Arguments> memberApis() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/cart"),
                Arguments.of(HttpMethod.POST, "/api/v1/cart/payment"),
                Arguments.of(HttpMethod.GET, "/api/v1/order/winner"),
                Arguments.of(HttpMethod.GET, "/api/v1/order/seller"),
                Arguments.of(HttpMethod.GET, "/api/v1/order/1"),
                Arguments.of(HttpMethod.POST, "/api/v1/order/1/confirm-purchase"),
                Arguments.of(HttpMethod.POST, "/api/v1/order/1/request-refund"),
                Arguments.of(HttpMethod.POST, "/api/v1/order/1/send-product")
        );
    }
}
