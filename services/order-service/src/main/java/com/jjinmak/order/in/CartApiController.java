package com.jjinmak.order.in;

import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.contracts.cart.dto.CartItemDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.order.app.OrderFacade;
import com.jjinmak.order.app.dto.PaymentResponseDto;
import com.jjinmak.order.in.dto.CartPaymentRequestDto;
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

    private final OrderFacade orderFacade;

    @Operation(
            summary = "장바구니 조회",
            description = "낙찰받은 후, 결제가 되기 전의 상품들을 조회합니다."
    )
    @GetMapping("")
    public ResponseEntity<RsData<List<CartItemDto>>> readWinnerItems(@AuthenticationPrincipal AuthPrincipal principal){

        List<CartItemDto> response = orderFacade.readWinnerItem(principal.memberId());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }

    @Operation(
            summary = "결제 요청",
            description = "여러 상품을 묶어 한 번에 결제를 시도합니다."
    )
    @PostMapping("/payment")
    public ResponseEntity<RsData<PaymentResponseDto>> paymentCartItems(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CartPaymentRequestDto request
    ){
        PaymentRequestDto req = orderFacade.createOrder(principal.memberId(), request.productIds());

        PaymentResultDto res = orderFacade.requestPayment(req);

        PaymentResponseDto response = orderFacade.handlePaymentResult(req.orderGroupId(), res);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }
}
