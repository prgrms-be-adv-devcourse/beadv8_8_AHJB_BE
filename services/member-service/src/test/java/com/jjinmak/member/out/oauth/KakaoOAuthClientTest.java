package com.jjinmak.member.out.oauth;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.config.KakaoOAuthProperties;
import com.jjinmak.member.exception.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KakaoOAuthClientTest {

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthProperties properties = new KakaoOAuthProperties(
                "client-id", "client-secret", "https://jjinmak.kro.kr/oauth/kakao/callback",
                Duration.ofSeconds(3), Duration.ofSeconds(5));
        client = new KakaoOAuthClient(builder.build(), properties);
    }

    @Test
    void 인가_코드를_교환하고_카카오_회원번호를_반환한다() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=auth-code")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andRespond(withSuccess("""
                        {"token_type":"bearer","access_token":"kakao-at","expires_in":21599,
                         "refresh_token":"kakao-rt","refresh_token_expires_in":5183999}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-at"))
                .andRespond(withSuccess("""
                        {"id":1234567890,"connected_at":"2026-11-18T00:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        String providerUserId = client.fetchProviderUserId("auth-code");

        assertThat(providerUserId).isEqualTo("1234567890");
        server.verify();
    }

    @Test
    void 카카오가_4xx를_주면_유효하지_않은_인가_코드로_처리한다() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URI))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":"invalid_grant","error_description":"authorization code not found","error_code":"KOE320"}
                                """));

        assertThatThrownBy(() -> client.fetchProviderUserId("used-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_OAUTH_CODE);
    }

    @Test
    void 카카오_서버_오류는_외부_인증_서버_오류로_처리한다() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URI))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchProviderUserId("auth-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.OAUTH_PROVIDER_ERROR);
    }

    @Test
    void 사용자_정보_조회_실패도_외부_인증_서버_오류로_처리한다() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"kakao-at\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(KakaoOAuthClient.USER_INFO_URI))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.fetchProviderUserId("auth-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.OAUTH_PROVIDER_ERROR);
    }
}
