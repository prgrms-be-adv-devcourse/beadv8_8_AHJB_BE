package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductWishFacade;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishResponseDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductWishApiController {

    private final ProductWishFacade productWishFacade;

    // 찜/찜 해제는 정지되지 않은 회원만 가능하다. (PERM_TRADE)
    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @PostMapping("/products/{productId}/wishes")
    public ResponseEntity<RsData<ProductWishResponseDto>> wish(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @PathVariable Long productId){
        ProductWishResponseDto response = productWishFacade.wish(principal.memberId(), productId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }

    @PreAuthorize("hasAuthority('PERM_TRADE')")
    @DeleteMapping("/products/{productId}/wishes")
    public ResponseEntity<RsData<ProductWishResponseDto>> unwish(@AuthenticationPrincipal AuthPrincipal principal,
                                                                 @PathVariable Long productId){
        ProductWishResponseDto response = productWishFacade.unwish(principal.memberId(), productId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    // 내 찜 목록은 로그인만 하면 조회할 수 있다.
    @GetMapping("/wishes")
    public ResponseEntity<RsData<ProductWishListResponseDto>> readMyWishes(@AuthenticationPrincipal AuthPrincipal principal,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int size){
        ProductWishListResponseDto response = productWishFacade.readMyWishes(principal.memberId(), page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }
}
