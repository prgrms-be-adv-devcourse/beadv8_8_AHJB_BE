package com.jjinmak.recommend.out.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjinmak.recommend.domain.SearchConditions;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchConditionResult(
        boolean recommendation,
        Long maxPrice,
        Long minPrice,
        String category,
        String manufacturer,
        List<String> keywords
) {
    public SearchConditions toConditions(){
        return new SearchConditions(maxPrice, minPrice, category, manufacturer, keywords);
    }
}
