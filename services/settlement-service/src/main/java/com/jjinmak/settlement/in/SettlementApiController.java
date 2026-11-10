package com.jjinmak.settlement.in;

import com.jjinmak.settlement.app.SettlementFacade;
import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.contracts.settlement.domain.SettlementStatus;
import com.jjinmak.settlement.in.dto.SettlementDetailDto;
import com.jjinmak.settlement.in.dto.SettlementListDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settlements")
public class SettlementApiController {

    private final SettlementFacade settlementFacade;

    @GetMapping
    public ResponseEntity<RsData<SettlementListDto>> getSettlements(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SettlementListDto response = settlementFacade.getSettlements(principal.memberId(), status, page, size);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<RsData<SettlementDetailDto>> getSettlement(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long settlementId
    ) {
        SettlementDetailDto response = settlementFacade.getSettlement(principal.memberId(), settlementId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
