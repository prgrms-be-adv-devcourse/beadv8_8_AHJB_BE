package com.jjinmak.contracts.settlement.dto;

import java.util.List;

public record SettlementListDto(
        List<SettlementItemDto> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
