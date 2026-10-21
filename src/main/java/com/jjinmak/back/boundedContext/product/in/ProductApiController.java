package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductFacade;
import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductDetailDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductUpcomingListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductUpdateRequestDto;
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
@RequestMapping("/api/v1/products")
public class ProductApiController {

    private final ProductFacade productFacade;

    // 상품 페이지 정보. 공개 조회로 열릴 수 있으므로 비로그인(principal == null)도 처리한다.
    // 곧 시작: 아직 경매가 시작되지 않은 상품 목록 (경매 목록 화면의 "곧 시작" 탭, 비로그인 조회 가능)
    @GetMapping("/upcoming")
    public ResponseEntity<RsData<ProductUpcomingListResponseDto>> readUpcoming(@RequestParam(required = false) String category,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "20") int size){
        ProductUpcomingListResponseDto response = productFacade.readUpcoming(category, page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<RsData<ProductDetailDto>> readDetail(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @PathVariable Long productId){
        Long memberId = principal == null ? null : principal.memberId();

        ProductDetailDto response = productFacade.readDetail(memberId, productId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    // 판매자 등록을 했고 정지되지 않은 회원만 상품을 등록/수정/삭제할 수 있다. (PERM_SELL)
    @PreAuthorize("hasAuthority('PERM_SELL')")
    @PostMapping
    public ResponseEntity<RsData<ProductCreateResponseDto>> createProduct(@AuthenticationPrincipal AuthPrincipal principal,
                                                                          @Valid @RequestBody ProductCreateRequestDto request){
        Product product = productFacade.createProduct(principal.memberId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(new ProductCreateResponseDto(product.getId())));
    }

    @PreAuthorize("hasAuthority('PERM_SELL')")
    @PatchMapping("/{productId}")
    public ResponseEntity<Void> updateProduct(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable Long productId,
                                              @Valid @RequestBody ProductUpdateRequestDto request){
        productFacade.updateProduct(principal.memberId(), productId, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PreAuthorize("hasAuthority('PERM_SELL')")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable Long productId){
        productFacade.deleteProduct(principal.memberId(), productId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
