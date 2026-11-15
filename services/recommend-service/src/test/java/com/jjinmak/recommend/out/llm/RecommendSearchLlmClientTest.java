package com.jjinmak.recommend.out.llm;

import com.jjinmak.recommend.domain.SearchConditions;
import com.jjinmak.recommend.out.row.SearchAuctionRow;
import com.jjinmak.recommend.support.FakeChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendSearchLlmClientTest {

    static final List<String> CATEGORIES = List.of("ACCESSORY", "GAME");
    static final List<String> MANUFACTURERS = List.of("NINTENDO", "SONY");

    final FakeChatModel chatModel = new FakeChatModel();
    final ObjectMapper objectMapper = JsonMapper.builder().build();
    final RecommendSearchLlmClient client = new RecommendSearchLlmClient(chatModel, objectMapper);

    @Test
    @DisplayName("조건 정하기는 LLM이 낸 JSON을 추천 여부와 조건으로 읽는다")
    void extractConditions_parsesLlmJson() {
        chatModel.respondConditions(
                "{\"recommendation\":true,\"maxPrice\":100000,\"minPrice\":null,\"category\":\"GAME\",\"manufacturer\":null,\"keywords\":[\"스위치\",\"레이싱\"]}");

        SearchConditionResult result = client.extractConditions("10만 원 이하 스위치 레이싱 게임", null, CATEGORIES, MANUFACTURERS);

        assertThat(result.recommendation()).isTrue();
        assertThat(result.toConditions()).isEqualTo(new SearchConditions(100000L, null, "GAME", null, List.of("스위치", "레이싱")));
    }

    @Test
    @DisplayName("조건 정하기 지시문에는 저장된 값 목록이, 입력에는 앞 조건과 회원의 말이 들어간다")
    void extractConditions_sendsValueListsPreviousConditionsAndQuestion() {
        SearchConditions previous = new SearchConditions(null, null, "GAME", null, List.of("스위치", "레이싱"));

        client.extractConditions("10만 원 이하로", previous, CATEGORIES, MANUFACTURERS);

        Prompt prompt = chatModel.conditionPrompts().getFirst();
        assertThat(prompt.getSystemMessage().getText())
                .contains("카테고리 값 목록: [ACCESSORY, GAME]", "제조사 값 목록: [NINTENDO, SONY]", "recommendation");
        assertThat(prompt.getUserMessage().getText())
                .isEqualTo("앞 조건: {\"maxPrice\":null,\"minPrice\":null,\"category\":\"GAME\",\"manufacturer\":null,\"keywords\":[\"스위치\",\"레이싱\"]}\n회원의 말: 10만 원 이하로");
    }

    @Test
    @DisplayName("앞 조건이 없으면 입력에 '없음'이라고 적는다")
    void extractConditions_withoutPrevious_saysNone() {
        client.extractConditions("스위치 레이싱 게임", null, CATEGORIES, MANUFACTURERS);

        assertThat(chatModel.conditionPrompts().getFirst().getUserMessage().getText())
                .isEqualTo("앞 조건: 없음\n회원의 말: 스위치 레이싱 게임");
    }

    @Test
    @DisplayName("조건 정하기는 저장된 값을 enum으로 넣은 엄격한 JSON 스키마를 응답 형식으로 쓴다")
    void extractConditions_usesStrictJsonSchema() {
        client.extractConditions("레이싱", null, CATEGORIES, MANUFACTURERS);

        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.conditionPrompts().getFirst().getOptions();
        OpenAiChatModel.ResponseFormat format = options.getResponseFormat();
        JsonNode schema = objectMapper.readTree(format.getJsonSchema());

        assertThat(format.getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
        assertThat(format.getStrict()).isTrue();
        assertThat(options.getMaxCompletionTokens()).isEqualTo(RecommendSearchLlmClient.CONDITION_MAX_TOKENS);
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.get("required")).map(JsonNode::asString)
                .containsExactly("recommendation", "maxPrice", "minPrice", "category", "manufacturer", "keywords");
        assertThat(schema.at("/properties/recommendation/type").asString()).isEqualTo("boolean");
        assertThat(schema.at("/properties/maxPrice/type")).map(JsonNode::asString).containsExactly("integer", "null");
        assertThat(schema.at("/properties/minPrice/type")).map(JsonNode::asString).containsExactly("integer", "null");
        assertThat(schema.at("/properties/category/anyOf/0/type").asString()).isEqualTo("string");
        assertThat(schema.at("/properties/category/anyOf/0/enum")).map(JsonNode::asString).containsExactly("ACCESSORY", "GAME");
        assertThat(schema.at("/properties/category/anyOf/1/type").asString()).isEqualTo("null");
        assertThat(schema.at("/properties/manufacturer/anyOf/0/enum")).map(JsonNode::asString).containsExactly("NINTENDO", "SONY");
        assertThat(schema.at("/properties/keywords/type").asString()).isEqualTo("array");
        assertThat(schema.at("/properties/keywords/items/type").asString()).isEqualTo("string");
    }

    @Test
    @DisplayName("저장된 값이 없는 조건은 스키마에서 null만 허용한다")
    void extractConditions_withoutStoredValues_allowsOnlyNull() {
        client.extractConditions("레이싱", null, List.of(), List.of());

        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.conditionPrompts().getFirst().getOptions();
        JsonNode schema = objectMapper.readTree(options.getResponseFormat().getJsonSchema());

        assertThat(schema.at("/properties/category/type").asString()).isEqualTo("null");
        assertThat(schema.at("/properties/manufacturer/type").asString()).isEqualTo("null");
        assertThat(schema.at("/properties/category/anyOf").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("요약 입력에는 회원의 말, 검색 조건, 찾은 경매의 상품명·카테고리·제조사·현재가·마감이 들어간다")
    void summarize_sendsQuestionConditionsAndAuctions() {
        SearchConditions conditions = new SearchConditions(100000L, null, null, null, List.of("레이싱"));
        List<SearchAuctionRow> rows = List.of(
                new SearchAuctionRow(1001L, 101L, "마리오 카트", "GAME", "NINTENDO", 45000L, LocalDateTime.of(2026, 11, 16, 20, 0)),
                new SearchAuctionRow(1002L, 102L, "그란투리스모", "GAME", "SONY", 60000L, LocalDateTime.of(2026, 11, 17, 9, 30))
        );
        chatModel.respondSummary("  레이싱 게임 경매 2개를 찾았어요.  ");

        String summary = client.summarize("10만 원 이하 레이싱 게임", conditions, rows);

        assertThat(summary).isEqualTo("레이싱 게임 경매 2개를 찾았어요.");
        Prompt prompt = chatModel.summaryPrompts().getFirst();
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
        assertThat(options.getResponseFormat()).isNull();
        assertThat(options.getMaxCompletionTokens()).isEqualTo(RecommendSearchLlmClient.SUMMARY_MAX_TOKENS);
        assertThat(prompt.getSystemMessage().getText()).contains("한 문장");
        assertThat(prompt.getUserMessage().getText()).isEqualTo("""
                회원의 말: 10만 원 이하 레이싱 게임
                검색 조건: {"maxPrice":100000,"minPrice":null,"category":null,"manufacturer":null,"keywords":["레이싱"]}
                찾은 경매 목록(2개):
                - 마리오 카트 | 카테고리 GAME | 제조사 NINTENDO | 현재가 45000원 | 마감 2026-11-16T20:00
                - 그란투리스모 | 카테고리 GAME | 제조사 SONY | 현재가 60000원 | 마감 2026-11-17T09:30
                """);
    }

    @Test
    @DisplayName("요약 응답이 비어 있으면 null을 돌려주고, 조건이 없으면 '없음'이라고 적는다")
    void summarize_blankResponse_returnsNull() {
        chatModel.respondSummary("   ");

        String summary = client.summarize("레이싱", null, List.of());

        assertThat(summary).isNull();
        assertThat(chatModel.summaryPrompts().getFirst().getUserMessage().getText())
                .contains("검색 조건: 없음", "찾은 경매 목록(0개):");
    }
}
