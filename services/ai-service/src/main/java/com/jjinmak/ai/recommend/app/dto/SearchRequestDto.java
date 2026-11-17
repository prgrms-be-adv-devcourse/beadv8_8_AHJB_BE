package com.jjinmak.ai.recommend.app.dto;

import com.jjinmak.ai.recommend.domain.SearchConditions;

public record SearchRequestDto(
        String query,
        SearchConditions conditions
) {
}
