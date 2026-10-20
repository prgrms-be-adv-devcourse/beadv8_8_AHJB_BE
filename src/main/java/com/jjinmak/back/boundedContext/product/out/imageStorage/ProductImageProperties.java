package com.jjinmak.back.boundedContext.product.out.imageStorage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 기본값은 로컬 개발/테스트용. dev 서버 값은 application-dev.yaml 참고
@ConfigurationProperties(prefix = "product-image")
public record ProductImageProperties(
        @DefaultValue("local")
        String storage,             // local | s3

        String bucket,

        @DefaultValue("ap-northeast-2")
        String region,

        @DefaultValue("local/")
        String keyPrefix,           // 환경별 경로 접두사 (dev 서버는 dev/)

        @DefaultValue("http://localhost:8080/local-images")
        String publicBaseUrl        // 이미지 조회 기본 URL
) {
    // 접두사를 붙인 실제 저장 경로 (예: dev/products/{UUID}.jpg)
    public String fullKey(String key) {
        return keyPrefix + key;
    }

    public String urlOf(String key) {
        return publicBaseUrl + "/" + fullKey(key);
    }
}
