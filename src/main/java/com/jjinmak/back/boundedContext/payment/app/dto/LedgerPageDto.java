package com.jjinmak.back.boundedContext.payment.app.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** 원장 조회 응답. 최신순 페이지 + 현재 잔액 (balance = 예치금, settlementBalance = 정산금) */
public record LedgerPageDto(
        long balance,
        long settlementBalance,
        List<LedgerEntryDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static LedgerPageDto of(long balance, long settlementBalance, Page<LedgerEntryDto> page) {
        return new LedgerPageDto(balance, settlementBalance, page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.hasNext());
    }
}
