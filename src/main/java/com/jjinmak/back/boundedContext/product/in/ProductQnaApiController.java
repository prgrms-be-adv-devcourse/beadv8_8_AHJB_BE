package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductQnaFacade;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaCreateRequestDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaUpdateRequestDto;
import com.jjinmak.back.global.rsData.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductQnaApiController {

    private final ProductQnaFacade productQnaFacade;

    // TODO: 인증 방식이 정해진 후에, 접속 유저 정보 가져오기
    private final Long memberDev = 1L;

    // after가 없으면 질문 목록을 페이지로 조회하고(답글 포함),
    // after가 있으면 그 이후 새로 작성된 질문/답글만 조회한다. (폴링용)
    @GetMapping("/products/{productId}/qnas")
    public ResponseEntity<RsData<ProductQnaListResponseDto>> readQnas(@PathVariable Long productId,
                                                                     @RequestParam(required = false) Long after,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "10") int size){
        ProductQnaListResponseDto response = after == null
                ? productQnaFacade.readQnas(productId, page, size)
                : productQnaFacade.readNewQnas(productId, after);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @PostMapping("/products/{productId}/qnas")
    public ResponseEntity<RsData<ProductQnaDto>> createQna(@PathVariable Long productId,
                                                           @Valid @RequestBody ProductQnaCreateRequestDto request){
        ProductQnaDto response = productQnaFacade.createQna(memberDev, productId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }

    @PatchMapping("/qnas/{qnaId}")
    public ResponseEntity<Void> updateQna(@PathVariable Long qnaId,
                                          @Valid @RequestBody ProductQnaUpdateRequestDto request){
        productQnaFacade.updateQna(memberDev, qnaId, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/qnas/{qnaId}")
    public ResponseEntity<Void> deleteQna(@PathVariable Long qnaId){
        productQnaFacade.deleteQna(memberDev, qnaId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
