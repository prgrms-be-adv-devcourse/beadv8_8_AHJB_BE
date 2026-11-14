package com.jjinmak.recommend.config;

import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityConfigTest extends RecommendIntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("인기 경매 조회는 회원 헤더 없이도 200을 돌려준다")
    void popular_withoutMemberHeader_isOpen() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("비슷한 경매 조회는 회원 헤더 없이도 열려 조회까지 진행한다 (없는 경매면 404 recommend002)")
    void similar_withoutMemberHeader_isOpen() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/auction/{auctionId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("recommend002"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST, /api/v1/recommend/popular",
            "PUT, /api/v1/recommend/popular",
            "PATCH, /api/v1/recommend/popular",
            "DELETE, /api/v1/recommend/popular",
            "POST, /api/v1/recommend/auction/1",
            "PUT, /api/v1/recommend/auction/1",
            "PATCH, /api/v1/recommend/auction/1",
            "DELETE, /api/v1/recommend/auction/1"
    })
    @DisplayName("회원 헤더 없이 열린 추천 API도 GET이 아닌 요청은 401 COMMON002를 돌려준다")
    void publicRecommendApis_nonGetWithoutMemberHeader_returnsUnauthorized(String method, String uri) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), uri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @Test
    @DisplayName("회원 맞춤 추천은 회원 헤더가 없으면 401 COMMON002를 돌려준다")
    void personal_withoutMemberHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @Test
    @DisplayName("회원 헤더가 있으면 인증을 통과해 회원 맞춤 추천을 돌려준다")
    void personal_withMemberHeader_passesAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/me")
                        .header(AuthHeaders.MEMBER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness", "/v3/api-docs", "/swagger-ui/index.html"})
    @DisplayName("헬스 체크와 API 문서는 회원 헤더 없이 열린다")
    void publicUrls_withoutMemberHeader_areOpen(String uri) throws Exception {
        mockMvc.perform(get(uri))
                .andExpect(status().isOk());
    }
}
