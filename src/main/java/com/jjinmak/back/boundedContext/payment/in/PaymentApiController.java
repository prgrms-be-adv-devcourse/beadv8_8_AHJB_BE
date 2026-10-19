package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.app.PaymentFacade;
import com.jjinmak.back.boundedContext.payment.app.WalletRetrySupport;
import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentDetailDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentQuoteDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.boundedContext.payment.in.dto.ChargeConfirmRequestDto;
import com.jjinmak.back.boundedContext.payment.in.dto.ChargeRequestDto;
import com.jjinmak.back.boundedContext.payment.in.dto.PaymentConfirmRequestDto;
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

@Tag(name = "결제", description = "토스페이먼츠 충전/충전 취소, 주문 결제 승인, 결제·충전 내역 조회 API")
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
            summary = "주문 결제 승인",
            description = "주문 결제 중 예치금이 부족해 토스 결제창을 거친 뒤, successUrl 로 돌아오면 호출합니다. "
                    + "paymentKey, orderId(pgOrderId), amount 를 그대로 넘기세요. 토스 승인 → 예치금 충전 → 주문 대금 홀딩까지 한 번에 끝나고, "
                    + "응답의 orderGroupId 로 주문 완료 화면으로 이동하면 됩니다. 주문 상태는 결제 완료 이벤트로 바뀝니다. "
                    + "같은 요청을 다시 보내면 이미 끝난 결제를 그대로 돌려줍니다(멱등). "
                    + "실패 응답의 retryable 이 true 면 '다시 결제하기'(주문 결제 요청부터)로 해결할 수 있습니다."
    )
    @PostMapping("/confirm")
    public ResponseEntity<RsData<PaymentSummaryDto>> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequestDto request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        PaymentSummaryDto response = retry.withRetry(() -> paymentFacade.confirmPayment(
                principal.memberId(), request.pgOrderId(), request.paymentKey(), request.amount()));
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
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

    // ===== 조회 =====

    @Operation(
            summary = "내 결제 내역 조회",
            description = "주문 묶음 단위의 결제 내역을 최신순으로 조회합니다. walletUsedAmount 는 예치금에서 나간 금액, chargeAmount 는 카드 등으로 결제한 부족분입니다. size 최대 50."
    )
    @GetMapping("/me")
    public ResponseEntity<RsData<PageDto<PaymentSummaryDto>>> listPayments(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageDto<PaymentSummaryDto> response = paymentFacade.listPayments(principal.memberId(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "내 결제 상세 조회",
            description = "결제에 포함된 주문별 금액·환불 여부와, 부족분 충전이 있었다면 그 충전 건(영수증 링크 포함)을 함께 돌려줍니다."
    )
    @GetMapping("/me/{paymentId}")
    public ResponseEntity<RsData<PaymentDetailDto>> getPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        PaymentDetailDto response = paymentFacade.getPayment(principal.memberId(), paymentId);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "내 충전 내역 조회",
            description = "수동 충전 내역을 최신순으로 조회합니다. cancelable 이 true 인 건만 취소 버튼을 보여주세요 (실제 취소는 잔액이 충전액 이상일 때만 성공). size 최대 50."
    )
    @GetMapping("/charges/me")
    public ResponseEntity<RsData<PageDto<ChargeSummaryDto>>> listCharges(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageDto<ChargeSummaryDto> response = paymentFacade.listCharges(principal.memberId(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "충전 상세 조회",
            description = "충전 1건의 상태·결제수단·토스 영수증 링크·실패 사유를 조회합니다."
    )
    @GetMapping("/charges/{chargeId}")
    public ResponseEntity<RsData<ChargeSummaryDto>> getCharge(
            @PathVariable Long chargeId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        ChargeSummaryDto response = paymentFacade.getCharge(principal.memberId(), chargeId);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "결제 전 견적",
            description = "지금 잔액으로 amount 를 결제하면 예치금에서 얼마, 카드로 얼마가 나가는지 미리 계산합니다. 저장하지 않으며 실제 결제 시점에 다시 계산됩니다."
    )
    @GetMapping("/quote")
    public ResponseEntity<RsData<PaymentQuoteDto>> quote(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam long amount
    ) {
        PaymentQuoteDto response = paymentFacade.quote(principal.memberId(), amount);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
