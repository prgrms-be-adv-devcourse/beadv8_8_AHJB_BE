package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.app.WalletFacade;
import com.jjinmak.back.boundedContext.payment.app.WalletRetrySupport;
import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.WithdrawalDto;
import com.jjinmak.back.boundedContext.payment.app.dto.HoldingPageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.HoldingRole;
import com.jjinmak.back.boundedContext.payment.app.dto.LedgerPageDto;
import com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletAccount;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.in.dto.BalanceResponseDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "지갑", description = "예치금 잔액·원장·홀딩(묶인 돈 / 정산 예정 금액) 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallet")
public class WalletApiController {

    private final WalletFacade walletFacade;
    private final WalletRetrySupport retry;   // 지갑 낙관적 락 충돌 재시도. 트랜잭션(파사드) 바깥에서 감싸야 한다

    @Operation(
            summary = "내 잔액 조회",
            description = "balance 는 예치금(결제에 쓸 수 있는 돈), settlementBalance 는 정산금(구매 확정으로 받은 판매 대금, 예치금과 분리 보관)입니다."
    )
    @GetMapping("/me")
    public ResponseEntity<RsData<BalanceResponseDto>> getBalance(@AuthenticationPrincipal AuthPrincipal principal) {
        BalanceResponseDto response = BalanceResponseDto.from(walletFacade.getBalances(principal.memberId()));
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "내 원장 조회",
            description = "충전·결제·환불·정산 등 입출금 내역을 최신순으로 조회합니다. amount 는 입금 +, 출금 - 이고 balanceAfter 는 그 시점의 해당 주머니 잔액입니다. "
                    + "account 를 주면 그 주머니만 (DEPOSIT = 예치금, SETTLEMENT = 정산금), "
                    + "type 을 주면 그 유형만 조회합니다 (예: SETTLEMENT_RECEIVE = 정산 수령 내역, REFUND_RECEIVE = 환불 수령 내역). size 최대 50."
    )
    @GetMapping("/me/ledger")
    public ResponseEntity<RsData<LedgerPageDto>> getLedger(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) WalletAccount account,
            @RequestParam(required = false) WalletTransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LedgerPageDto response = walletFacade.getLedger(principal.memberId(), account, type, page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "내 홀딩 조회 (묶인 돈 / 정산 예정 금액)",
            description = "role=BUYER 면 내가 결제한 돈이 어느 주문에 묶여 있는지, role=SELLER 면 내가 받을 정산 예정 금액을 주문별로 조회합니다. "
                    + "heldTotal 은 아직 정산·환불되지 않은 합계(BUYER 는 묶인 금액, SELLER 는 정산액), settleableTotal 은 구매 확정이 끝나 정산만 남은 합계입니다. "
                    + "status 를 주면 그 상태만 (HELD / CONFIRMED / RELEASED / REFUNDED). size 최대 50."
    )
    @GetMapping("/me/holdings")
    public ResponseEntity<RsData<HoldingPageDto>> getHoldings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "BUYER") HoldingRole role,
            @RequestParam(required = false) HoldingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        HoldingPageDto response = walletFacade.getHoldings(principal.memberId(), role, status, page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "예치금 전액 출금 (명목상)",
            description = "보유 중인 예치금 전액을 등록된 계좌로 환급한 것으로 기록합니다. 현재 실제 송금은 이루어지지 않으며 잔액이 0 이 되고 출금 내역과 원장(WITHDRAWAL)에 남습니다. "
                    + "정산금은 출금되지 않습니다. 출금 후에는 이전 충전 건을 취소할 수 없으니, 취소 가능한 충전이 있으면 먼저 안내해 주세요. 잔액이 0 이면 WALLET601."
    )
    @PostMapping("/me/withdrawals")
    public ResponseEntity<RsData<WithdrawalDto>> withdrawAll(@AuthenticationPrincipal AuthPrincipal principal) {
        WithdrawalDto response = retry.withRetry(() -> walletFacade.withdrawAll(principal.memberId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new RsData<>(response));
    }

    @Operation(summary = "내 출금 내역 조회", description = "최신순. size 최대 50.")
    @GetMapping("/me/withdrawals")
    public ResponseEntity<RsData<PageDto<WithdrawalDto>>> listWithdrawals(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageDto<WithdrawalDto> response = walletFacade.listWithdrawals(principal.memberId(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
