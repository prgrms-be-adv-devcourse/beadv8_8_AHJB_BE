package com.jjinmak.member.in;

import com.jjinmak.member.config.CorsProperties;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OriginValidator {

    private final CorsProperties corsProperties;

    public void validate(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);

        if (origin == null) {
            return;
        }
        if (!corsProperties.allowedOrigins().contains(origin)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}