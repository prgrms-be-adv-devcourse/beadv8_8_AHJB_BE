package com.jjinmak.back.boundedContext.payment.app.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 홀딩 조회 응답. 목록 + 합계.
 * - heldTotal       : 아직 정산·환불되지 않은 홀딩(HELD + CONFIRMED)의 합.
 *                     BUYER 면 묶여 있는 내 돈(amount 합), SELLER 면 받을 돈(settlementAmount 합)
 * - settleableTotal : 그중 구매 확정이 끝나 정산만 남은 금액 (CONFIRMED). SELLER 에게 의미 있음
 */
public record HoldingPageDto(
        HoldingRole role,
        long heldTotal,
        long settleableTotal,
        List<HoldingSummaryDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static HoldingPageDto of(HoldingRole role, long heldTotal, long settleableTotal, Page<HoldingSummaryDto> page) {
        return new HoldingPageDto(role, heldTotal, settleableTotal, page.getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
    }
}
