package com.jjinmak.ai.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchConditionsTest {

    @Test
    @DisplayName("키워드가 null이면 빈 목록으로 두고, 빈 문자열과 공백은 버리고 앞뒤 공백을 지운다")
    void keywords_areNormalized() {
        assertThat(new SearchConditions(null, null, null, null, null).keywords()).isEmpty();
        assertThat(new SearchConditions(null, null, null, null, Arrays.asList(" 스위치 ", "", null, "  ", "레이싱")).keywords())
                .containsExactly("스위치", "레이싱");
    }

    @Test
    @DisplayName("none()은 조건이 하나도 없는 상태다")
    void none_hasNoConditions() {
        assertThat(SearchConditions.none()).isEqualTo(new SearchConditions(null, null, null, null, List.of()));
    }

    @Test
    @DisplayName("키워드가 없으면 임베딩할 문장이 없다")
    void createQueryText_withoutKeywords_isNull() {
        assertThat(new SearchConditions(100000L, null, "GAME", "SONY", List.of()).createQueryText()).isNull();
    }

    @Test
    @DisplayName("임베딩할 문장은 상품 텍스트와 같은 형식이고, 카테고리와 제조사는 있을 때만 들어간다")
    void createQueryText_followsProductTextFormat() {
        assertThat(new SearchConditions(null, null, null, null, List.of("스위치", "레이싱")).createQueryText())
                .isEqualTo("상품명: 스위치 레이싱");
        assertThat(new SearchConditions(null, null, "GAME", null, List.of("레이싱")).createQueryText())
                .isEqualTo("상품명: 레이싱 / 카테고리: GAME");
        assertThat(new SearchConditions(null, null, null, "SONY", List.of("레이싱")).createQueryText())
                .isEqualTo("상품명: 레이싱 / 제조사: SONY");
        assertThat(new SearchConditions(100000L, 10000L, "GAME", "NINTENDO", List.of("스위치", "레이싱")).createQueryText())
                .isEqualTo("상품명: 스위치 레이싱 / 카테고리: GAME / 제조사: NINTENDO");
    }
}
