package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.domain.ProductErrorCode;
import com.jjinmak.back.global.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// spring.servlet.multipart 크기 제한(10MB)을 넘으면 컨트롤러에 도달하기 전에 예외가 난다.
// 공통 예외 처리에 핸들러가 없어 500이 되므로, 상품 이미지 에러코드(PRODUCT018)로 응답한다.
// 현재 multipart 업로드는 상품 이미지에서만 사용한다.
@RestControllerAdvice
public class ProductImageUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e){
        ProductErrorCode code = ProductErrorCode.IMAGE_TOO_LARGE;

        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponse(false, code.getCode(), code.getMessage()));
    }
}
