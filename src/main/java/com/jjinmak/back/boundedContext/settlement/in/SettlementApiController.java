package com.jjinmak.back.boundedContext.settlement.in;

import com.jjinmak.back.boundedContext.settlement.app.SettlementFacade;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.global.security.AuthPrincipal;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import com.jjinmak.back.shared.settlement.dto.SettlementListDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
