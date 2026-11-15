package com.jjinmak.recommend.app.dto;

import com.jjinmak.recommend.domain.SearchConditions;

public record SearchRequestDto(
        String query,
        SearchConditions conditions
) {
}
