package com.jjinmak.ai.recommend.app.dto;

import com.jjinmak.ai.recommend.domain.SearchConditions;

import java.util.List;

public record SearchResponseDto(
        SearchConditions conditions,
        String summary,
        List<AuctionResponseDto> auctions
) {
}
