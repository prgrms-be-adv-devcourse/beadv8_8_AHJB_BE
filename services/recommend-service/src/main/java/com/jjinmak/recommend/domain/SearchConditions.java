package com.jjinmak.recommend.domain;

import java.util.ArrayList;
import java.util.List;

public record SearchConditions(
        Long maxPrice,
        Long minPrice,
        String category,
        String manufacturer,
        List<String> keywords
) {
    public SearchConditions {
        keywords = keywords == null ? List.of() : keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::strip)
                .toList();
    }

    public static SearchConditions none(){
        return new SearchConditions(null, null, null, null, List.of());
    }

    public String createQueryText(){
        if (keywords.isEmpty())
            return null;

        List<String> parts = new ArrayList<>();
        parts.add("상품명: " + String.join(" ", keywords));
        if (category != null)
            parts.add("카테고리: " + category);
        if (manufacturer != null)
            parts.add("제조사: " + manufacturer);

        return String.join(" / ", parts);
    }
}
