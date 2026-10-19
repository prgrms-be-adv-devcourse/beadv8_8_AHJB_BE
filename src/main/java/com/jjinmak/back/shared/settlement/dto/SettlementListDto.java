package com.jjinmak.back.shared.settlement.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record SettlementListDto(
        List<SettlementItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static SettlementListDto from(Page<SettlementItemDto> page) {
        return new SettlementListDto(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext()
        );
    }
}