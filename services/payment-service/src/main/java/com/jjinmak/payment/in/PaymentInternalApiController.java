package com.jjinmak.payment.in;

import com.jjinmak.contracts.payment.dto.HoldingSettlementDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.payment.app.facade.PaymentFacade;
import com.jjinmak.payment.app.facade.WalletFacade;
import com.jjinmak.payment.app.support.WalletRetrySupport;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class PaymentInternalApiController {
    private final PaymentFacade paymentFacade;
    private final WalletFacade walletFacade;
    private final WalletRetrySupport retry;

    @Operation(
            summary = "주문 건 결제 요청",
            description = "주문에 대한 결제를 처리합니다. 주문에서 호출합니다."
    )
    @PostMapping("/payments/request")
    public ResponseEntity<PaymentResultDto> request(
            @RequestBody PaymentRequestDto dto
    ) {
        PaymentResultDto response = retry.withRetry(() -> paymentFacade.requestPayment(dto));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(
            summary = "정산 정보 조회",
            description = "주문 1건에 대한 정산 정보를 조회합니다. 정산에서 정산 전에 수수료/정산액을 확인할 때 사용합니다."
    )
    @GetMapping("/wallet/settlement/{orderId}")
    public ResponseEntity<HoldingSettlementDto> getSettlementInfo(
            @PathVariable Long orderId
    ) {
        HoldingSettlementDto response = walletFacade.getSettlement(orderId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
