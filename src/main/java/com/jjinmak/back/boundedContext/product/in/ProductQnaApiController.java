package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductQnaFacade;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaCreateRequestDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductQnaUpdateRequestDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductQnaApiController {

    private final ProductQnaFacade productQnaFacade;

    // after가 없으면 질문 목록을 페이지로 조회하고(답글 포함),
    // after가 있으면 그 이후 새로 작성된 질문/답글만 조회한다. (폴링용)
    // 로그인 회원 정보를 쓰지 않으므로, 공개 조회로 열려도 그대로 동작한다.
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

    // Q&A 작성/수정/삭제는 정지되지 않은 회원만 가능하다. (PERM_TRADE)
    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PostMapping("/products/{productId}/qnas")
    public ResponseEntity<RsData<ProductQnaDto>> createQna(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @PathVariable Long productId,
                                                           @Valid @RequestBody ProductQnaCreateRequestDto request){
        ProductQnaDto response = productQnaFacade.createQna(principal.memberId(), productId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }

    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PatchMapping("/qnas/{qnaId}")
    public ResponseEntity<Void> updateQna(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable Long qnaId,
                                          @Valid @RequestBody ProductQnaUpdateRequestDto request){
        productQnaFacade.updateQna(principal.memberId(), qnaId, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @DeleteMapping("/qnas/{qnaId}")
    public ResponseEntity<Void> deleteQna(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable Long qnaId){
        productQnaFacade.deleteQna(principal.memberId(), qnaId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
