package com.jjinmak.back.boundedContext.product.out.imageStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

// 로컬 개발/테스트용. 파일을 로컬 폴더(product-image.local-dir)에 저장하고 /local-images/** 로 보여준다. (AWS 자격 증명 불필요)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "product-image", name = "storage", havingValue = "local", matchIfMissing = true)
public class LocalProductImageStorage implements ProductImageStorage {

    private final ProductImageProperties properties;

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        Path path = pathOf(key);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("로컬 이미지 저장 실패: " + path, e);
        }
        log.info("[로컬 이미지 저장소] 업로드 path={}, size={}, contentType={}", path, bytes.length, contentType);
    }

    @Override
    public void delete(String key) {
        Path path = pathOf(key);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("로컬 이미지 삭제 실패: " + path, e);
        }
        log.info("[로컬 이미지 저장소] 삭제 path={}", path);
    }

    @Override
    public String urlOf(String key) {
        return properties.urlOf(key);
    }

    // key는 서버가 UUID로 만든 값이라 폴더 밖으로 벗어나지 않는다.
    private Path pathOf(String key) {
        return Path.of(properties.localDir()).resolve(properties.fullKey(key));
    }
}
