package com.jjinmak.back.boundedContext.product.out.imageStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 로컬 개발/테스트용. 파일은 저장하지 않고 URL만 만든다. (AWS 자격 증명 없이 업로드 흐름을 확인할 수 있다)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "product-image", name = "storage", havingValue = "local", matchIfMissing = true)
public class LocalProductImageStorage implements ProductImageStorage {

    private final ProductImageProperties properties;

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        log.info("[로컬 이미지 저장소] 업로드 생략 key={}, size={}, contentType={}", properties.fullKey(key), bytes.length, contentType);
    }

    @Override
    public void delete(String key) {
        log.info("[로컬 이미지 저장소] 삭제 생략 key={}", properties.fullKey(key));
    }

    @Override
    public String urlOf(String key) {
        return properties.urlOf(key);
    }
}
