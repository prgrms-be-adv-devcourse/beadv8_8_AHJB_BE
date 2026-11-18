package com.jjinmak.common.security;

import com.jjinmak.common.exception.CommonErrorCode;
import com.jjinmak.common.exception.ErrorCode;
import com.jjinmak.common.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 인증이 필요한 요청에 인증 정보(X-Member-Id 헤더)가 없을 때 401을 응답한다.
 * 토큰 만료·위조 같은 토큰 에러는 Gateway가 응답하므로 여기서는 다루지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        writeErrorResponse(response, CommonErrorCode.UNAUTHORIZED);
    }

    // Security 필터 체인은 MVC 밖이라 @RestControllerAdvice가 동작하지 않기 때문에 응답을 직접 작성한다.
    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(
                false,
                errorCode.getCode(),
                errorCode.getMessage()
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}