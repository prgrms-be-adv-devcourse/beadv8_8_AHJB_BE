package com.jjinmak.member.out.oauth;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.app.OAuthUserProfile;
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

        OAuthUserProfile profile = client.fetchUserProfile("auth-code");

        assertThat(profile.providerUserId()).isEqualTo("1234567890");
        assertThat(profile.verifiedEmail()).isNull();
        server.verify();
    }

    private void expectToken() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"kakao-at\"}", MediaType.APPLICATION_JSON));
    }

    private void expectUser(String json) {
        server.expect(requestTo(KakaoOAuthClient.USER_INFO_URI))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void 유효하고_인증된_이메일이면_함께_반환한다() {
        expectToken();
        expectUser("""
                {"id":1234567890,
                 "kakao_account":{"email_needs_agreement":false,"is_email_valid":true,
                                  "is_email_verified":true,"email":"user@kakao.com"}}
                """);

        OAuthUserProfile profile = client.fetchUserProfile("auth-code");

        assertThat(profile.providerUserId()).isEqualTo("1234567890");
        assertThat(profile.verifiedEmail()).isEqualTo("user@kakao.com");
    }

    @Test
    void 카카오_닉네임을_함께_반환한다() {
        expectToken();
        expectUser("""
                {"id":1,
                 "properties":{"nickname":"예전필드"},
                 "kakao_account":{"profile_nickname_needs_agreement":false,
                                  "profile":{"nickname":"카카오닉네임","is_default_nickname":false}}}
                """);

        assertThat(client.fetchUserProfile("auth-code").nickname()).isEqualTo("카카오닉네임");
    }

    @Test
    void 닉네임이_없거나_비어_있으면_null을_반환한다() {
        expectToken();
        expectUser("""
                {"id":1,"kakao_account":{"profile":{"nickname":" "}}}
                """);

        assertThat(client.fetchUserProfile("auth-code").nickname()).isNull();
    }

    @Test
    void 인증되지_않은_이메일은_사용하지_않는다() {
        expectToken();
        expectUser("""
                {"id":1,"kakao_account":{"is_email_valid":true,"is_email_verified":false,"email":"user@kakao.com"}}
                """);

        assertThat(client.fetchUserProfile("auth-code").verifiedEmail()).isNull();
    }

    @Test
    void 만료된_이메일은_사용하지_않는다() {
        expectToken();
        expectUser("""
                {"id":1,"kakao_account":{"is_email_valid":false,"is_email_verified":true,"email":"user@kakao.com"}}
                """);

        assertThat(client.fetchUserProfile("auth-code").verifiedEmail()).isNull();
    }

    @Test
    void 이메일_제공에_동의하지_않았으면_이메일_없이_반환한다() {
        expectToken();
        expectUser("""
                {"id":1,"kakao_account":{"email_needs_agreement":true}}
                """);

        OAuthUserProfile profile = client.fetchUserProfile("auth-code");

        assertThat(profile.providerUserId()).isEqualTo("1");
        assertThat(profile.verifiedEmail()).isNull();
        assertThat(profile.nickname()).isNull();
    }

    @Test
    void 카카오가_4xx를_주면_유효하지_않은_인가_코드로_처리한다() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URI))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":"invalid_grant","error_description":"authorization code not found","error_code":"KOE320"}
                                """));

        assertThatThrownBy(() -> client.fetchUserProfile("used-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_OAUTH_CODE);
    }

    @Test
    void 카카오_서버_오류는_외부_인증_서버_오류로_처리한다() {
        server.expect(requestTo(KakaoOAuthClient.TOKEN_URI))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUserProfile("auth-code"))
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

        assertThatThrownBy(() -> client.fetchUserProfile("auth-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.OAUTH_PROVIDER_ERROR);
    }
}
