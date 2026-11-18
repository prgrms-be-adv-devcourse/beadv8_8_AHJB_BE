package com.jjinmak.back.global.security;

import com.jjinmak.back.global.exception.CommonErrorCode;
import com.jjinmak.back.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        response.setStatus(CommonErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");


        ErrorResponse errorResponse = new ErrorResponse(
                false,
                CommonErrorCode.FORBIDDEN.getCode(),
                CommonErrorCode.FORBIDDEN.getMessage()
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
