package com.jjinmak.refund.in;

import com.jjinmak.refund.app.RefundFacade;
import com.jjinmak.common.rsData.RsData;
import com.jjinmak.common.security.AuthPrincipal;
import com.jjinmak.refund.in.dto.RefundDetailDto;
import com.jjinmak.contracts.refund.dto.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
public class RefundApiController {

    private final RefundFacade refundFacade;

    @GetMapping("/{refundId}")
    public ResponseEntity<RsData<RefundDetailDto>> getRefund(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long refundId
    ) {
        RefundDetailDto response = refundFacade.getRefund(principal.memberId(), refundId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @PatchMapping("/{refundId}/reject")
    public ResponseEntity<RsData<RefundDto>> rejectRefund(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long refundId
    ) {
        RefundDto response = refundFacade.rejectRefund(principal.memberId(), refundId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @PatchMapping("/{refundId}/accept")
    public ResponseEntity<RsData<RefundDto>> acceptRefund(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long refundId
    ) {
        RefundDto response = refundFacade.acceptRefund(principal.memberId(), refundId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping
    public ResponseEntity<RsData<RefundDetailDto>> getRefundByOrder(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam Long orderId
    ) {
        RefundDetailDto response = refundFacade.getRefundByOrder(principal.memberId(), orderId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
