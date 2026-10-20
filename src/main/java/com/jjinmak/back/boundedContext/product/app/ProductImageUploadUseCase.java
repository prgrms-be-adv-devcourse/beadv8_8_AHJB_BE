package com.jjinmak.back.boundedContext.product.app;

import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.boundedContext.product.domain.ProductImage;
import com.jjinmak.back.boundedContext.product.domain.ProductImageFormat;
import com.jjinmak.back.boundedContext.product.domain.ProductMember;
import com.jjinmak.back.boundedContext.product.in.dto.ProductImageUploadResponseDto;
import com.jjinmak.back.boundedContext.product.out.ProductImageRepository;
import com.jjinmak.back.boundedContext.product.out.ProductMemberRepository;
import com.jjinmak.back.boundedContext.product.out.imageStorage.ProductImageStorage;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageUploadUseCase {

    public static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024; // 10MB

    private final ProductMemberRepository productMemberRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageStorage productImageStorage;

    // 검증 → DB 저장 → 저장소 업로드. 업로드가 실패하면 트랜잭션이 롤백되어 DB 행도 남지 않는다.
    public ProductImageUploadResponseDto upload(Long uploaderId, MultipartFile file){
        ProductMember uploader = productMemberRepository.findById(uploaderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.USER_NOT_FOUND));

        if (file == null || file.isEmpty()){
            throw new BusinessException(ProductErrorCode.INVALID_IMAGE_FILE);
        }
        if (file.getSize() > MAX_IMAGE_SIZE){
            throw new BusinessException(ProductErrorCode.IMAGE_TOO_LARGE);
        }

        byte[] bytes = readBytes(file);

        // 확장자/Content-Type이 아니라 실제 파일 내용으로 형식을 판별한다. (위장 파일 차단)
        ProductImageFormat format = ProductImageFormat.detect(bytes)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.INVALID_IMAGE_FILE));

        // 원래 파일명은 쓰지 않는다. (경로 조작, 덮어쓰기 방지)
        String key = "products/" + UUID.randomUUID() + "." + format.getExtension();

        ProductImage image = productImageRepository.save(new ProductImage(uploader, key, format, bytes.length));

        productImageStorage.upload(key, bytes, format.getContentType());

        return new ProductImageUploadResponseDto(image.getId(), productImageStorage.urlOf(key));
    }

    private byte[] readBytes(MultipartFile file){
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ProductErrorCode.INVALID_IMAGE_FILE);
        }
    }
}
