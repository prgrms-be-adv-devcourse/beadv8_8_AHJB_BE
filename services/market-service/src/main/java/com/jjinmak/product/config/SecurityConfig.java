package com.jjinmak.product.config;

import com.jjinmak.common.security.CustomAccessDeniedHandler;
import com.jjinmak.common.security.CustomAuthenticationEntryPoint;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import com.jjinmak.product.product.out.imageStorage.ProductImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// 토큰 검증은 게이트웨이가 맡고, 이 서비스는 게이트웨이가 넣어준 회원 헤더로 인증한다.
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    private static final String[] PUBLIC_URLS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        // 경매 목록 화면의 "곧 시작" 탭 (경매 목록과 같이 비로그인 조회 가능)
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/upcoming").permitAll()
                        // 로컬 저장소 이미지 조회 (<img> 요청에는 토큰이 없다)
                        .requestMatchers(HttpMethod.GET, ProductImageProperties.LOCAL_URL_PATH).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
