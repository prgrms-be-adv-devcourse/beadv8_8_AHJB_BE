package com.jjinmak.back.boundedContext.order.in.cart;

import com.jjinmak.back.boundedContext.order.app.cart.CartFacade;
import com.jjinmak.back.boundedContext.order.in.cart.dto.CartPaymentRequestDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "장바구니", description = "낙찰 후 구매 예정된 상품을 관리하는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
public class CartApiController {

    private final CartFacade cartFacade;

    @Operation(
            summary = "장바구니 조회",
            description = "낙찰받은 후, 결제가 되기 전의 상품들을 조회합니다."
    )
    @GetMapping("")
    public ResponseEntity<RsData<List<CartItemDto>>> readWinnerItems(@AuthenticationPrincipal AuthPrincipal principal){

        List<CartItemDto> response = cartFacade.readWinnerItems(principal.memberId());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @Operation(
            summary = "결제 요청",
            description = "여러 상품을 묶어 한 번에 결제를 시도합니다."
    )
    @PostMapping("/payment")
    public ResponseEntity<RsData<Void>> paymentCartItems(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CartPaymentRequestDto request
    ){
        cartFacade.paymentCartItems(principal.memberId(), request.productIds());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(null));
    }
}
