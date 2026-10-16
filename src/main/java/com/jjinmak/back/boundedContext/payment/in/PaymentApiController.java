package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.app.PaymentFacade;
import com.jjinmak.back.boundedContext.payment.app.WalletRetrySupport;
import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.LedgerPageDto;
import com.jjinmak.back.boundedContext.payment.in.dto.BalanceResponseDto;
import com.jjinmak.back.boundedContext.payment.in.dto.ChargeConfirmRequestDto;
import com.jjinmak.back.boundedContext.payment.in.dto.ChargeRequestDto;
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

@Tag(name = "결제", description = "예치금 잔액 조회와 토스페이먼츠 충전/충전 취소 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentApiController {

    private final PaymentFacade paymentFacade;
    private final WalletRetrySupport retry;   // 지갑 낙관적 락 충돌 재시도. 트랜잭션(파사드) 바깥에서 감싸야 한다


    @Operation(
            summary = "충전 요청",
            description = "결제창을 띄우기 전에 호출합니다. 응답의 pgOrderId 를 토스 SDK 의 orderId 로, amount 를 결제 금액으로, orderName 을 상품명으로 넘기세요."
    )
    @PostMapping("/charges")
    public ResponseEntity<RsData<ChargeSummaryDto>> requestCharge(
            @Valid @RequestBody ChargeRequestDto request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        ChargeSummaryDto response = paymentFacade.requestCharge(principal.memberId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(response));
    }

    @Operation(
            summary = "충전 승인",
            description = "토스 결제창에서 결제 후 successUrl 로 돌아오면 호출합니다. paymentKey, orderId(pgOrderId), amount 를 그대로 넘기세요. 승인 성공 시 예치금에 반영됩니다."
    )
    @PostMapping("/charges/confirm")
    public ResponseEntity<RsData<ChargeSummaryDto>> confirmCharge(
            @Valid @RequestBody ChargeConfirmRequestDto request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        ChargeSummaryDto response = retry.withRetry(() -> paymentFacade.confirmCharge(
                principal.memberId(), request.pgOrderId(), request.paymentKey(), request.amount()));
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "충전 취소",
            description = "아직 사용하지 않은 충전을 전액 취소합니다. 예치금에서 차감한 뒤 토스 결제를 취소합니다. 예치금 잔액이 충전액보다 적으면 취소할 수 없습니다."
    )
    @PostMapping("/charges/{chargeId}/cancel")
    public ResponseEntity<RsData<ChargeSummaryDto>> cancelCharge(
            @PathVariable Long chargeId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        ChargeSummaryDto response = retry.withRetry(() -> paymentFacade.cancelCharge(principal.memberId(), chargeId));
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
