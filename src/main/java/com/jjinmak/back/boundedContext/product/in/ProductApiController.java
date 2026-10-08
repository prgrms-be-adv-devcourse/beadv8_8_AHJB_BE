package com.jjinmak.back.boundedContext.product.in;

import com.jjinmak.back.boundedContext.product.app.ProductFacade;
import com.jjinmak.back.boundedContext.product.domain.Product;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateRequestDto;
import com.jjinmak.back.boundedContext.product.in.dto.ProductCreateResponseDto;
import com.jjinmak.back.global.rsData.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductApiController {

    private final ProductFacade productFacade;

    // TODO: 인증 방식이 정해진 후에, 접속 유저 정보 가져오기
    private final Long sellerDev = 1L;

    @PostMapping
    public ResponseEntity<RsData<ProductCreateResponseDto>> createProduct(@Valid @RequestBody ProductCreateRequestDto request){
        Product product = productFacade.createProduct(sellerDev, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(new ProductCreateResponseDto(product.getId())));
    }
}
