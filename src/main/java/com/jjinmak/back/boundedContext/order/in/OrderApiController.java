package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.app.OrderFacade;
import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.boundedContext.order.in.dto.OrderDeliveryDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "주문", description = "결제를 요청해 생성된 주문서를 관리하는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order")
public class OrderApiController {

    private final OrderFacade orderFacade;

    @Operation(
            summary = "결제 완료된 주문서 조회",
            description = "낙찰자로서 결제 완료된 주문서를 조회합니다."
                    + "(결제 완료, 배송 중, 배송 완료, 구매 확정, 환불 요청, 환불 완료 상태)"
    )
    @GetMapping("/winner")
    public ResponseEntity<RsData<List<OrderDto>>> readWinnerOrders(@AuthenticationPrincipal AuthPrincipal principal){

        List<OrderDto> response = orderFacade.readWinnerOrders(principal.memberId());

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "판매한 주문서 조회",
            description = "판매자로서 결제가 완료된 이후의 주문서를 조회합니다. "
                    + "(결제 완료, 배송 중, 배송 완료, 구매 확정, 환불 요청, 환불 완료 상태)"
    )
    @GetMapping("/seller")
    public ResponseEntity<RsData<List<OrderDto>>> readSellerOrders(@AuthenticationPrincipal AuthPrincipal principal){
        List<OrderDto> response = orderFacade.readSellerOrders(principal.memberId());

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "주문서 상세 조회",
            description = "주문 id로 주문서를 조회합니다. 해당 주문의 낙찰자 또는 판매자만 조회할 수 있습니다."
    )
    @GetMapping("/{orderId}")
    public ResponseEntity<RsData<OrderDto>> readOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthPrincipal principal
    ){
        OrderDto response = orderFacade.readOrder(principal.memberId(), orderId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "구매 확정",
            description = "배송이 완료된 주문을 낙찰자가 구매 확정합니다. 배송 완료 후 7일이 지나면 자동으로 구매 확정됩니다."
    )
    @PostMapping("/{orderId}/confirm-purchase")
    public ResponseEntity<RsData<Void>> confirmPurchase(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthPrincipal principal
    ){
        orderFacade.confirmPurchase(principal.memberId(), orderId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(null));
    }

    @Operation(
            summary = "환불 요청",
            description = "배송이 완료된 주문에 대해 낙찰자가 환불을 요청합니다. 구매 확정 전에만 요청할 수 있습니다."
    )
    @PostMapping("/{orderId}/request-refund")
    public ResponseEntity<RsData<Void>> requestRefund(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthPrincipal principal
    ){
        orderFacade.requestRefund(principal.memberId(), orderId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(null));
    }

    @Operation(
            summary = "상품 발송 등록",
            description = "결제가 완료된 주문에 대해 판매자가 택배사와 송장번호를 등록합니다. 배송 정보가 등록되면 주문이 배송 중 상태로 바뀝니다."
    )
    @PostMapping("/{orderId}/send-product")
    public ResponseEntity<RsData<Void>> requestDelivery(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderDeliveryDto dto,
            @AuthenticationPrincipal AuthPrincipal principal
    ){
        orderFacade.requestDelivery(principal.memberId(), orderId, dto.carrier(), dto.waybill());

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(null));
    }
}
