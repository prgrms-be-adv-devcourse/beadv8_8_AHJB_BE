package com.jjinmak.back.boundedContext.refund.in;

import com.jjinmak.back.boundedContext.refund.app.RefundFacade;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.shared.refund.dto.RefundDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
public class RefundApiController {

    private final RefundFacade refundFacade;

    private final Long memberDev = 1L;

    @GetMapping("/{refundId}")
    public ResponseEntity<RsData<RefundDetailDto>> getRefund(@PathVariable Long refundId) {
        RefundDetailDto response = refundFacade.getRefund(memberDev, refundId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
