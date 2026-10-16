package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.app.WalletFacade;
import com.jjinmak.back.boundedContext.payment.app.dto.LedgerPageDto;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "지갑", description = "예치금 잔액 조회와 충전 내역 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallet")
public class WalletApiController {

    private final WalletFacade walletFacade;

    @Operation(summary = "내 예치금 잔액 조회")
    @GetMapping("/me")
    public ResponseEntity<RsData<BalanceResponseDto>> getBalance(@AuthenticationPrincipal AuthPrincipal principal) {
        long balance = walletFacade.getBalance(principal.memberId());
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(new BalanceResponseDto(balance)));
    }

    @Operation(
            summary = "내 예치금 원장 조회",
            description = "충전·결제·환불·정산 등 예치금 입출금 내역을 최신순으로 조회합니다. amount 는 입금 +, 출금 - 이고 balanceAfter 는 그 시점 잔액입니다. size 최대 50."
    )
    @GetMapping("/me/ledger")
    public ResponseEntity<RsData<LedgerPageDto>> getLedger(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LedgerPageDto response = walletFacade.getLedger(principal.memberId(), page, size);
        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
