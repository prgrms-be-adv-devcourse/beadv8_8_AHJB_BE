package com.jjinmak.back.boundedContext.product.out.imageStorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(ProductImageProperties.class)
public class ProductImageStorageConfig {

    // 자격 증명은 SDK 기본 방식을 쓴다. (dev 서버는 IAM 역할, Access Key 불필요)
    @Bean
    @ConditionalOnProperty(prefix = "product-image", name = "storage", havingValue = "s3")
    public S3Client productImageS3Client(ProductImageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
