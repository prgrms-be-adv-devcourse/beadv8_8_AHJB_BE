package com.jjinmak.back.boundedContext.product.out.imageStorage;

import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "product-image", name = "storage", havingValue = "s3")
public class S3ProductImageStorage implements ProductImageStorage {

    private final S3Client productImageS3Client;
    private final ProductImageProperties properties;

    @Override
    public void upload(String key, byte[] bytes, String contentType) {
        try {
            productImageS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(properties.fullKey(key))
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            log.error("S3 이미지 업로드 실패 key={}", properties.fullKey(key), e);
            throw new BusinessException(ProductErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String key) {
        productImageS3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(properties.fullKey(key))
                        .build());
    }

    @Override
    public String urlOf(String key) {
        return properties.urlOf(key);
    }
}
