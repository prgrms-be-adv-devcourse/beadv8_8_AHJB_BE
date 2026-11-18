package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductImageFacade;
import com.jjinmak.back.boundedContext.product.in.dto.ProductImageUploadResponseDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/images")
public class ProductImageApiController {

    private final ProductImageFacade productImageFacade;

    // 상품 이미지 1장 업로드. 받은 imageId를 상품 등록/수정의 imageIds로 보낸다. (판매자만)
    @PreAuthorize("hasAuthority('PERM_SELL')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RsData<ProductImageUploadResponseDto>> upload(@AuthenticationPrincipal AuthPrincipal principal,
                                                                        @RequestPart("file") MultipartFile file){
        ProductImageUploadResponseDto response = productImageFacade.upload(principal.memberId(), file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }
}
