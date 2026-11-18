package com.jjinmak.member.out.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.member.app.OAuthProviderClient;
import com.jjinmak.member.config.KakaoOAuthProperties;
import com.jjinmak.member.exception.AuthErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

/**
 * 카카오 로그인 REST API 클라이언트.
 * https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api
 */
@Slf4j
@Component
public class KakaoOAuthClient implements OAuthProviderClient {

    static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final KakaoOAuthProperties properties;

    public KakaoOAuthClient(@Qualifier("kakaoRestClient") RestClient restClient,
                            KakaoOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String fetchProviderUserId(String authorizationCode) {
        String kakaoAccessToken = exchangeToken(authorizationCode);
        return String.valueOf(fetchUserId(kakaoAccessToken));
    }

    private String exchangeToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    // 4xx: 만료/재사용된 코드가 대부분이지만, redirect-uri·client-secret 설정 오류도 여기로 온다.
                    // 원인 파악을 위해 카카오 에러 본문(error_code 등, 비밀값 없음)을 남긴다.
                    .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                        log.warn("카카오 토큰 교환 실패 status={} body={}", res.getStatusCode(),
                                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                        throw new BusinessException(AuthErrorCode.INVALID_OAUTH_CODE);
                    })
                    .body(KakaoTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return response.accessToken();
        } catch (RestClientException e) {
            // 5xx, 타임아웃, 연결 실패
            log.error("카카오 토큰 교환 중 통신 오류", e);
            throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private Long fetchUserId(String kakaoAccessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);

            if (response == null || response.id() == null) {
                throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return response.id();
        } catch (RestClientException e) {
            // 방금 발급받은 토큰으로 조회하므로 4xx 도 우리 쪽에서 복구할 수 없는 외부 오류로 본다
            log.error("카카오 사용자 정보 조회 중 오류", e);
            throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    // 동의항목 없이 항상 내려오는 회원번호만 사용한다
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoUserResponse(Long id) {
    }
}
