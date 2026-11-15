package com.jjinmak.recommend.app.dto;

import com.jjinmak.recommend.domain.SearchConditions;

import java.util.List;

public record SearchResponseDto(
        SearchConditions conditions,
        String summary,
        List<AuctionResponseDto> auctions
) {
}
