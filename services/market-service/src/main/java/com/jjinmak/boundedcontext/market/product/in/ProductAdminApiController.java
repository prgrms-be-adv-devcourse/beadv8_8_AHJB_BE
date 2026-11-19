package com.jjinmak.boundedcontext.market.product.in;

import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import com.jjinmak.boundedcontext.market.product.domain.Product;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductDutchCreateRequestDto;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductDutchCreateResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 상품 API. 관리자는 회원 DB에서 직접 지정한다. (ROLE_ADMIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class ProductAdminApiController {

    private final ProductFacade productFacade;

    // 네덜란드식 이벤트 상품 등록. 판매자는 관리자 본인이고, 오픈 시각에 경매가 시작된다.
    @PostMapping("/dutch")
    public ResponseEntity<RsData<ProductDutchCreateResponseDto>> createDutchProduct(@AuthenticationPrincipal AuthPrincipal principal,
                                                                                   @Valid @RequestBody ProductDutchCreateRequestDto request){
        Product product = productFacade.createDutchProduct(principal.memberId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(ProductDutchCreateResponseDto.from(product)));
    }
}
