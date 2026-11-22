package com.jjinmak.global.config;

import com.jjinmak.boundedcontext.market.product.out.imageStorage.ProductImageProperties;
import com.jjinmak.common.security.CustomAccessDeniedHandler;
import com.jjinmak.common.security.CustomAuthenticationEntryPoint;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
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

// 토큰 검증은 게이트웨이가 맡고, 이 서비스는 게이트웨이가 넣어 준 회원 헤더로 인증한다.
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class MarketSecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    private static final String[] PUBLIC_URLS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health/**",
            "/actuator/prometheus"
    };

    // 상품·경매 상세는 id가 숫자일 때만 연다. (/products/me, /auctions/me/bids 같은 로그인 전용 경로가 같이 열리지 않도록)
    private static final String[] PUBLIC_GET_URLS = {
            "/api/v1/products/upcoming",
            "/api/v1/products/{productId:\\d+}",
            "/api/v1/products/{productId:\\d+}/qnas",
            ProductImageProperties.LOCAL_URL_PATH,
            "/api/v1/auctions",
            "/api/v1/auctions/dutch",
            "/api/v1/auctions/{auctionId:\\d+}",
            "/api/v1/auctions/{auctionId:\\d+}/bids",
            "/api/v1/auctions/{auctionId:\\d+}/stream"   // 실시간 구독 (#557): 상세와 같은 공개 범위
    };

    @Bean
    public SecurityFilterChain marketFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_URLS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new HeaderAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}