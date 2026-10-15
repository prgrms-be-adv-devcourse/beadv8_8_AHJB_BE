package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductWishFacade;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishListResponseDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductWishResponseDto;
import com.jjinmak.back.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductWishApiController {

    private final ProductWishFacade productWishFacade;

    // TODO: 인증 방식이 정해진 후에, 접속 유저 정보 가져오기
    private final Long memberDev = 1L;

    @PostMapping("/products/{productId}/wishes")
    public ResponseEntity<RsData<ProductWishResponseDto>> wish(@PathVariable Long productId){
        ProductWishResponseDto response = productWishFacade.wish(memberDev, productId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }

    @DeleteMapping("/products/{productId}/wishes")
    public ResponseEntity<RsData<ProductWishResponseDto>> unwish(@PathVariable Long productId){
        ProductWishResponseDto response = productWishFacade.unwish(memberDev, productId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @GetMapping("/wishes")
    public ResponseEntity<RsData<ProductWishListResponseDto>> readMyWishes(@RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int size){
        ProductWishListResponseDto response = productWishFacade.readMyWishes(memberDev, page, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }
}
