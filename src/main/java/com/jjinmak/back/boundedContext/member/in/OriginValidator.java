package com.jjinmak.back.boundedContext.member.in;

import com.jjinmak.back.global.config.CorsProperties;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OriginValidator {

    private final CorsProperties corsProperties;

    // 쿠키는 브라우저가 자동으로 전송하므로, 쿠키를 읽는 요청은 허용된 출처에서 왔는지 확인한다.
    // CSRF는 브라우저에서만 발생하고 브라우저는 POST 요청에 항상 Origin을 보내므로,
    // Origin이 없는 요청(서버 간 호출, API 도구)은 통과시킨다.
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