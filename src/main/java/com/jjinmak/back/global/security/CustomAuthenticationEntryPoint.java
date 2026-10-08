package com.jjinmak.back.global.security;

import com.jjinmak.back.global.exception.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {

        Object attribute = request.getAttribute(JwtAuthenticationFilter.EXCEPTION_ATTRIBUTE);

        ErrorCode errorCode = (attribute instanceof AuthErrorCode code)
                ? code
                : CommonErrorCode.UNAUTHORIZED;

        writeErrorResponse(response, errorCode);

    }

    // Security 필터 체인은 MVC 밖이라 @RestControllerAdvice가 동작하지 않기 때문에 응답을 직접 작성한다.
    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException{

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
