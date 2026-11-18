package com.jjinmak.back.boundedContext.product.out.imageStorage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

// 로컬 저장소에 저장한 이미지 파일을 /local-images/** 로 내려준다. (dev 서버는 S3 URL을 쓰므로 등록하지 않는다)
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "product-image", name = "storage", havingValue = "local", matchIfMissing = true)
public class LocalProductImageWebConfig implements WebMvcConfigurer {

    private final ProductImageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.localDir()).toAbsolutePath().toUri().toString();
        registry.addResourceHandler(ProductImageProperties.LOCAL_URL_PATH)
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
