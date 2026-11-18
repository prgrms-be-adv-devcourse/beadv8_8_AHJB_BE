package com.jjinmak.back.payment.in.controller;

import com.jjinmak.back.payment.app.dto.*;
import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.back.payment.app.facade.PaymentFacade;
import com.jjinmak.back.payment.app.support.WalletRetrySupport;
import com.jjinmak.back.payment.app.dto.*;
import com.jjinmak.back.payment.in.dto.ChargeConfirmRequestDto;
import com.jjinmak.back.payment.in.dto.ChargeRequestDto;
import com.jjinmak.back.payment.in.dto.PaymentConfirmRequestDto;
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
            description = "결제창을 띄우기 전에 호출합니다. 응답의 pgOrderId 를 토스 SDK 의 orderId 로, amount 를 결제 금액으로, orderName 을 상품명으로, "
                    + "pgMethod 를 method 로 넘기세요. 예치금 충전은 계좌이체(TRANSFER)로만 받으며, 다른 수단으로 결제하면 승인 단계에서 자동 취소됩니다(CHARGE009). "
                    + "금액이 최소·최대 충전 금액 범위를 벗어나면 CHARGE010."
    )
    @PostMapping("/charges")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.ChargeSummaryDto>> requestCharge(
            @Valid @RequestBody ChargeRequestDto request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        com.jjinmak.back.payment.app.dto.ChargeSummaryDto response = paymentFacade.requestCharge(principal.memberId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(response));
    }

    @Operation(
            summary = "주문 결제 승인",
            description = "주문 결제 중 토스 결제창을 거친 뒤(예치금 부족분 충전 또는 직접 결제), successUrl 로 돌아오면 호출합니다. "
                    + "paymentKey, orderId(pgOrderId), amount 를 그대로 넘기세요. "
                    + "부족분 충전이면 토스 승인 → 예치금 충전 → 주문 대금 홀딩까지 한 번에 끝나고, 계좌이체가 아닌 수단으로 결제했으면 자동 취소됩니다(CHARGE009). "
                    + "직접 결제면 결제한 돈이 예치금을 거치지 않고 바로 주문 대금이 되며, 실제로 승인된 결제수단이 pgMethod 로 기록됩니다. "
                    + "결제창을 띄운 뒤 결제수단이나 포인트 사용액을 바꿔 다시 결제 요청을 했다면 예전 결제창의 승인은 거절됩니다(CHARGE012). "
                    + "결제창에 있는 동안 포인트나 예치금이 줄어 결제할 수 없게 됐으면 토스 승인 전에 거절됩니다(WALLET204 / WALLET201). "
                    + "응답의 orderGroupId 로 주문 완료 화면으로 이동하면 됩니다. 주문 상태는 결제 완료 이벤트로 바뀝니다. "
                    + "같은 요청을 다시 보내면 이미 끝난 결제를 그대로 돌려줍니다(멱등). "
                    + "실패 응답의 retryable 이 true 면 '다시 결제하기'(주문 결제 요청부터)로 해결할 수 있습니다."
    )
    @PostMapping("/confirm")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.PaymentSummaryDto>> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequestDto request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        com.jjinmak.back.payment.app.dto.PaymentSummaryDto response = retry.withRetry(() -> paymentFacade.confirmPayment(
                principal.memberId(), request.pgOrderId(), request.paymentKey(), request.amount()));
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "충전 승인",
            description = "토스 결제창에서 결제 후 successUrl 로 돌아오면 호출합니다. paymentKey, orderId(pgOrderId), amount 를 그대로 넘기세요. 승인 성공 시 예치금에 반영됩니다. "
                    + "계좌이체가 아닌 수단으로 결제했으면 자동 취소됩니다(CHARGE009)."
    )
    @PostMapping("/charges/confirm")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.ChargeSummaryDto>> confirmCharge(
            @Valid @RequestBody ChargeConfirmRequestDto request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        com.jjinmak.back.payment.app.dto.ChargeSummaryDto response = retry.withRetry(() -> paymentFacade.confirmCharge(
                principal.memberId(), request.pgOrderId(), request.paymentKey(), request.amount()));
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "충전 취소",
            description = "아직 사용하지 않은 충전을 전액 취소합니다. 예치금에서 차감한 뒤 토스 결제를 취소합니다. 예치금 잔액이 충전액보다 적으면 취소할 수 없습니다."
    )
    @PostMapping("/charges/{chargeId}/cancel")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.ChargeSummaryDto>> cancelCharge(
            @PathVariable Long chargeId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        com.jjinmak.back.payment.app.dto.ChargeSummaryDto response = retry.withRetry(() -> paymentFacade.cancelCharge(principal.memberId(), chargeId));
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    // ===== 조회 =====

    @Operation(
            summary = "내 결제 내역 조회",
            description = "주문 묶음 단위의 결제 내역을 최신순으로 조회합니다. 총액을 무엇으로 냈는지는 pointAmount(포인트) + walletUsedAmount(예치금) + pgAmount(직접 결제)로 나뉩니다. "
                    + "payMethod 는 고른 결제수단, pgMethod 는 직접 결제가 실제로 승인된 수단, chargeAmount 는 결제창에서 결제한 금액(부족분 충전 또는 직접 결제)입니다. size 최대 50."
    )
    @GetMapping("/me")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.PageDto<com.jjinmak.back.payment.app.dto.PaymentSummaryDto>>> listPayments(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        com.jjinmak.back.payment.app.dto.PageDto<com.jjinmak.back.payment.app.dto.PaymentSummaryDto> response = paymentFacade.listPayments(principal.memberId(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "내 결제 상세 조회",
            description = "결제 금액 구성(포인트·예치금·직접 결제), 결제에 포함된 주문별 금액·환불 여부와, 결제창을 거쳤다면 그 토스 결제 건(부족분 충전 또는 직접 결제, 영수증 링크 포함)을 함께 돌려줍니다. "
                    + "직접 결제 건의 canceledAmount 는 주문 환불로 토스에 취소된 금액의 합입니다."
    )
    @GetMapping("/me/{paymentId}")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.PaymentDetailDto>> getPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        com.jjinmak.back.payment.app.dto.PaymentDetailDto response = paymentFacade.getPayment(principal.memberId(), paymentId);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "내 충전 내역 조회",
            description = "수동 충전 내역을 최신순으로 조회합니다. cancelable 이 true 인 건만 취소 버튼을 보여주세요 (실제 취소는 잔액이 충전액 이상일 때만 성공). size 최대 50."
    )
    @GetMapping("/charges/me")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.PageDto<com.jjinmak.back.payment.app.dto.ChargeSummaryDto>>> listCharges(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        com.jjinmak.back.payment.app.dto.PageDto<com.jjinmak.back.payment.app.dto.ChargeSummaryDto> response = paymentFacade.listCharges(principal.memberId(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "충전 상세 조회",
            description = "충전 1건의 상태·결제수단·토스 영수증 링크·실패 사유를 조회합니다."
    )
    @GetMapping("/charges/{chargeId}")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.ChargeSummaryDto>> getCharge(
            @PathVariable Long chargeId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        com.jjinmak.back.payment.app.dto.ChargeSummaryDto response = paymentFacade.getCharge(principal.memberId(), chargeId);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "결제 전 견적",
            description = "amount 를 payMethod(DEPOSIT 예치금 / TRANSFER 계좌이체 직접 결제 / CARD 카드·간편결제 직접 결제, 생략하면 DEPOSIT)와 "
                    + "pointUseAmount(포인트 사용액, 생략하면 0)로 결제하면 무엇으로 얼마씩 나가고 결제창에서 얼마를 결제해야 하는지(pgAmount, pgMethod) 미리 계산합니다. "
                    + "예치금 결제에서 부족분(shortage)이 최소 충전 금액보다 작으면 chargeAmount 는 최소 충전 금액으로 올라가고, 차액은 결제 후 예치금에 남습니다(balanceAfterPayment). "
                    + "chargeLimitExceeded 가 true 면 계좌이체 1회 한도를 넘어 결제 요청이 거절됩니다(예치금 결제면 예치금을 먼저 충전, 계좌이체 직접 결제면 다른 수단 선택). "
                    + "expectedEarnPoint 는 이 조건으로 결제하고 구매 확정했을 때 적립될 것으로 예상되는 포인트입니다. 예치금과 계좌이체 직접 결제 금액에만 적립되고 "
                    + "카드·간편결제, 포인트 사용액, 배송비에는 적립되지 않습니다. 배송비를 빼고 계산하려면 deliveryFee(amount 에 포함된 배송비 합, 생략하면 0)를 함께 보내세요. "
                    + "포인트 사용액이 결제 금액을 넘으면 PAYMENT010, 포인트 잔액보다 많으면 WALLET204. 저장하지 않으며 실제 결제 시점에 다시 계산됩니다."
    )
    @GetMapping("/quote")
    public ResponseEntity<RsData<com.jjinmak.back.payment.app.dto.PaymentQuoteDto>> quote(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam long amount,
            @RequestParam(required = false) String payMethod,
            @RequestParam(defaultValue = "0") long pointUseAmount,
            @RequestParam(defaultValue = "0") long deliveryFee
    ) {
        com.jjinmak.back.payment.app.dto.PaymentQuoteDto response = paymentFacade.quote(
                principal.memberId(), amount, payMethod, pointUseAmount, deliveryFee);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
