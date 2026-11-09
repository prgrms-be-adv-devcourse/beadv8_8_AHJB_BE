package com.jjinmak.gateway.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MEMBER_ID = "X-Member-Id";
    private static final String MEMBER_ROLES = "X-Member-Roles";
    private static final PathPattern SKIP_PATH = PathPatternParser.defaultInstance.parse("/api/v1/auth/**");

    private final JwtTokenVerifier verifier;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 외부에서 들어온 인증 헤더는 무조건 제거
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(MEMBER_ID);
                    headers.remove(MEMBER_ROLES);
                })
                .build();

        // 2. 인증 경로는 토큰을 보지 않음 (재발급 무한 반복 방지)
        if (SKIP_PATH.matches(request.getPath().pathWithinApplication())) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        // 3. 토큰이 없으면 익명으로 통과 (막을지는 서비스가 판단)
        String token = resolveToken(request);
        if (token == null) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        // 4. 토큰 검증
        TokenVerification result = verifier.verify(token);
        return switch (result.status()) {
            case VALID -> chain.filter(exchange.mutate()
                    .request(request.mutate()
                            .header(MEMBER_ID, String.valueOf(result.memberId()))
                            .header(MEMBER_ROLES, result.roles())
                            .build())
                    .build());
            case EXPIRED -> writeError(exchange, AuthError.EXPIRED_ACCESS_TOKEN);
            case INVALID -> writeError(exchange, AuthError.INVALID_ACCESS_TOKEN);
        };
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private Mono<Void> writeError(ServerWebExchange exchange, AuthError error) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"isSuccess\":false,\"errorCode\":\"%s\",\"message\":\"%s\"}"
                .formatted(error.code(), error.message());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}