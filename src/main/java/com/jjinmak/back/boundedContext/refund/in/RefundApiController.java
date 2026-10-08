package com.jjinmak.back.boundedContext.refund.in;

import com.jjinmak.back.boundedContext.refund.app.RefundFacade;
import com.jjinmak.back.boundedContext.refund.in.dto.RefundCreateRequestDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
public class RefundApiController {

    private final RefundFacade refundFacade;

    private final Long memberDev = 1L;

    @PostMapping
    public ResponseEntity<RsData<RefundDto>> createRefund(@Valid @RequestBody RefundCreateRequestDto request) {
        RefundDto response = refundFacade.createRefund(
                memberDev,
                request.orderId(),
                request.reason(),
                request.detail()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RsData<>(response));
    }
}
