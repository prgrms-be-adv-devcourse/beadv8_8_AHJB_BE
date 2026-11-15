package com.jjinmak.recommend.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.exception.CommonErrorCode;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.app.dto.AuctionResponseDto;
import com.jjinmak.recommend.app.dto.SearchRequestDto;
import com.jjinmak.recommend.app.dto.SearchResponseDto;
import com.jjinmak.recommend.domain.SearchConditions;
import com.jjinmak.recommend.in.RecommendKafkaListener;
import com.jjinmak.recommend.out.repository.InteractionLogRepository;
import com.jjinmak.recommend.out.repository.InteractionRepository;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.recommend.support.FakeChatModel;
import com.jjinmak.recommend.support.FakeEmbeddingModel;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static com.jjinmak.contracts.product.event.ProductChangeType.DELETED;
import static com.jjinmak.recommend.exception.RecommendErrorCode.SEARCH_TOO_MANY_REQUESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendSearchTest extends RecommendIntegrationTestSupport {

    static final long SELLER_ID = 9L;
    static final long START_PRICE = 10000L;
    static final int DIMENSIONS = 1536;
    static final String OTHER_MODEL = "text-embedding-3-small";
    static long nextMemberId = 100L;

    @Autowired RecommendFacade recommendFacade;
    @Autowired RecommendKafkaListener recommendKafkaListener;
    @Autowired RecommendAuctionRepository recommendAuctionRepository;
    @Autowired RecommendProductRepository recommendProductRepository;
    @Autowired RecommendMemberRepository recommendMemberRepository;
    @Autowired InteractionRepository interactionRepository;
    @Autowired InteractionLogRepository interactionLogRepository;
    @Autowired ProductEmbeddingRepository productEmbeddingRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired FakeChatModel fakeChatModel;
    @Autowired FakeEmbeddingModel fakeEmbeddingModel;

    @Value("${spring.ai.openai.embedding.model}")
    String model;

    LocalDateTime now;
    long memberId;

    @BeforeEach
    void setUp() {
        clean();
        fakeChatModel.reset();
        fakeEmbeddingModel.reset();
        memberId = nextMemberId++;
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        readyAuction(101L, 1001L, 120, "GAME", "NINTENDO");
        readyAuction(102L, 1002L, 0, "GAME", "SONY");
        readyAuction(103L, 1003L, 60, "ACCESSORY", "SONY");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("첫 질문은 LLM이 뽑은 조건을 돌려주고, 키워드 문장의 임베딩과 가까운 순서로 경매를 고르고, 요약을 붙인다")
    void search_firstQuestion_ranksByKeywordEmbedding() {
        fakeChatModel.respondConditions(conditions(true, null, null, null, null, "\"스위치\",\"레이싱\""));

        SearchResponseDto response = recommendFacade.searchAuctions(memberId, new SearchRequestDto("스위치 레이싱 게임 보여 줘", null));

        assertThat(auctionIds(response.auctions())).containsExactly(1002L, 1003L, 1001L);
        assertThat(response.conditions()).isEqualTo(new SearchConditions(null, null, null, null, List.of("스위치", "레이싱")));
        assertThat(response.summary()).isEqualTo(FakeChatModel.DEFAULT_SUMMARY_RESPONSE);
        assertThat(fakeEmbeddingModel.requestedTexts()).containsExactly("상품명: 스위치 레이싱");
        assertThat(userText(fakeChatModel.conditionPrompts().getFirst())).contains("앞 조건: 없음", "회원의 말: 스위치 레이싱 게임 보여 줘");
        assertThat(userText(fakeChatModel.summaryPrompts().getFirst())).contains("상품101", "상품102", "상품103", "찾은 경매 목록(3개)");
    }

    @Test
    @DisplayName("응답 항목에는 경매 id, 상품 id, 카테고리, 현재가, 종료 시각이 들어간다")
    void search_mapsAuctionFields() {
        fakeChatModel.respondConditions(conditions(true, null, null, "ACCESSORY", null, "\"레이싱\""));

        SearchResponseDto response = search("레이싱 주변기기");

        assertThat(response.auctions()).containsExactly(new AuctionResponseDto(1003L, 103L, "ACCESSORY", START_PRICE, now.plusDays(1)));
    }

    @Test
    @DisplayName("앞 조건이 있으면 LLM에 앞 조건을 함께 보내고, 새 조건과 그 키워드 문장으로 검색한다")
    void search_sendsPreviousConditionsToLlm() {
        SearchConditions previous = new SearchConditions(null, null, "GAME", null, List.of("스위치", "레이싱"));
        fakeChatModel.respondConditions(conditions(true, 100000L, null, "GAME", null, "\"스위치\",\"레이싱\""));

        SearchResponseDto response = recommendFacade.searchAuctions(memberId, new SearchRequestDto("10만 원 이하로", previous));

        assertThat(userText(fakeChatModel.conditionPrompts().getFirst())).contains("\"category\":\"GAME\"", "\"스위치\"", "회원의 말: 10만 원 이하로");
        assertThat(response.conditions()).isEqualTo(new SearchConditions(100000L, null, "GAME", null, List.of("스위치", "레이싱")));
        assertThat(fakeEmbeddingModel.requestedTexts()).containsExactly("상품명: 스위치 레이싱 / 카테고리: GAME");
        assertThat(auctionIds(response.auctions())).containsExactly(1002L, 1001L);
    }

    @Test
    @DisplayName("가격은 현재가로, 카테고리와 제조사는 저장된 값으로 거른다")
    void search_filtersByPriceCategoryAndManufacturer() {
        price(1002L, 30000L);
        price(1003L, 15000L);

        fakeChatModel.respondConditions(conditions(true, 20000L, null, "GAME", null, "\"레이싱\""));
        assertThat(auctionIds(search("2만 원 이하 레이싱 게임").auctions())).containsExactly(1001L);

        fakeChatModel.respondConditions(conditions(true, null, 12000L, null, "SONY", "\"레이싱\""));
        assertThat(auctionIds(search("소니 레이싱").auctions())).containsExactly(1002L, 1003L);

        fakeChatModel.respondConditions(conditions(true, null, null, "GAME", "SONY", "\"레이싱\""));
        assertThat(auctionIds(search("소니 게임").auctions())).containsExactly(1002L);
    }

    @Test
    @DisplayName("임베딩이 READY가 아니거나 설정 모델이 아닌 경매는 키워드 검색에서 빠지고, 인기 순에는 들어간다")
    void search_excludesUnusableEmbeddingsOnlyForKeywordSearch() {
        readyAuction(104L, 1004L, 0, "GAME", "SONY");
        readyAuction(105L, 1005L, 0, "GAME", "SONY");
        jdbcTemplate.update("update product_embedding set status = 'PENDING' where id = 104");
        jdbcTemplate.update("update product_embedding set model = ? where id = 105", OTHER_MODEL);

        fakeChatModel.respondConditions(conditions(true, null, null, null, null, "\"레이싱\""));
        assertThat(auctionIds(search("레이싱").auctions())).containsExactly(1002L, 1003L, 1001L);

        fakeChatModel.respondConditions(conditions(true, null, null, null, null, ""));
        assertThat(auctionIds(search("아무거나").auctions())).containsExactlyInAnyOrder(1001L, 1002L, 1003L, 1004L, 1005L);
    }

    @Test
    @DisplayName("본인이 판매하는 경매는 빠지고, 결과가 없으면 정해 둔 요약을 돌려주며 요약을 요청하지 않는다")
    void search_excludesOwnAuctions_andSkipsSummaryWhenEmpty() {
        fakeChatModel.respondConditions(conditions(true, null, null, null, null, "\"레이싱\""));

        SearchResponseDto response = recommendFacade.searchAuctions(SELLER_ID, new SearchRequestDto("레이싱", null));

        assertThat(response.auctions()).isEmpty();
        assertThat(response.summary()).isEqualTo(RecommendSearchUseCase.NO_RESULT_SUMMARY);
        assertThat(fakeChatModel.summaryPrompts()).isEmpty();
    }

    @Test
    @DisplayName("추천 요청이 아니면 안내 문장과 빈 목록, 앞 조건을 그대로 돌려주고 검색과 요약을 하지 않는다")
    void search_nonRecommendation_returnsGuidance() {
        SearchConditions previous = new SearchConditions(null, null, null, null, List.of("스위치"));
        fakeChatModel.respondConditions(conditions(false, null, null, null, null, ""));

        SearchResponseDto response = recommendFacade.searchAuctions(memberId, new SearchRequestDto("입찰은 어떻게 해요?", previous));

        assertThat(response.summary()).isEqualTo(RecommendSearchUseCase.GUIDANCE_MESSAGE);
        assertThat(response.auctions()).isEmpty();
        assertThat(response.conditions()).isEqualTo(previous);
        assertThat(fakeChatModel.summaryPrompts()).isEmpty();
        assertThat(fakeEmbeddingModel.requestedTexts()).isEmpty();
    }

    @Test
    @DisplayName("조건 정하기가 실패하면 앞 조건을 그대로 쓴다")
    void search_conditionFailure_keepsPreviousConditions() {
        SearchConditions previous = new SearchConditions(null, null, "GAME", null, List.of("레이싱"));
        fakeChatModel.failOnConditions();

        SearchResponseDto response = recommendFacade.searchAuctions(memberId, new SearchRequestDto("PS5로 바꿔 줘", previous));

        assertThat(response.conditions()).isEqualTo(previous);
        assertThat(fakeEmbeddingModel.requestedTexts()).containsExactly("상품명: 레이싱 / 카테고리: GAME");
        assertThat(auctionIds(response.auctions())).containsExactly(1002L, 1001L);
        assertThat(response.summary()).isEqualTo(FakeChatModel.DEFAULT_SUMMARY_RESPONSE);
    }

    @Test
    @DisplayName("조건 정하기가 실패하고 앞 조건도 없으면 질문 문장을 그대로 임베딩하고 조건 없이 찾는다")
    void search_conditionFailureWithoutPrevious_embedsQuestion() {
        fakeChatModel.failOnConditions();

        SearchResponseDto response = recommendFacade.searchAuctions(memberId, new SearchRequestDto("스위치 레이싱 게임", null));

        assertThat(response.conditions()).isNull();
        assertThat(fakeEmbeddingModel.requestedTexts()).containsExactly("스위치 레이싱 게임");
        assertThat(auctionIds(response.auctions())).containsExactly(1002L, 1003L, 1001L);
    }

    @Test
    @DisplayName("키워드가 없으면 임베딩하지 않고, 조건으로 거른 뒤 입찰한 회원 수 순으로 고른다")
    void search_withoutKeywords_ordersByPopularity() {
        bidders(1003L, 103L, 21L, 22L);
        bidders(1002L, 102L, 23L);
        price(1001L, 60000L);
        fakeChatModel.respondConditions(conditions(true, 50000L, null, null, null, ""));

        SearchResponseDto response = search("5만 원 이하 아무거나");

        assertThat(fakeEmbeddingModel.requestedTexts()).isEmpty();
        assertThat(auctionIds(response.auctions())).containsExactly(1003L, 1002L);
        assertThat(response.conditions()).isEqualTo(new SearchConditions(50000L, null, null, null, List.of()));
    }

    @Test
    @DisplayName("질의 임베딩이 실패하면 조건으로만 거르고 인기 순으로 고른다")
    void search_embeddingFailure_fallsBackToPopularity() {
        bidders(1002L, 102L, 21L, 22L);
        fakeEmbeddingModel.failOn("상품명: 레이싱");
        fakeChatModel.respondConditions(conditions(true, null, null, null, null, "\"레이싱\""));

        SearchResponseDto response = search("레이싱");

        assertThat(auctionIds(response.auctions())).containsExactly(1002L, 1001L, 1003L);
        assertThat(response.summary()).isEqualTo(FakeChatModel.DEFAULT_SUMMARY_RESPONSE);
    }

    @Test
    @DisplayName("요약이 실패하면 요약 없이 목록을 돌려준다")
    void search_summaryFailure_returnsListWithoutSummary() {
        fakeChatModel.respondConditions(conditions(true, null, null, null, null, "\"레이싱\""));
        fakeChatModel.failOnSummary();

        SearchResponseDto response = search("레이싱");

        assertThat(response.summary()).isNull();
        assertThat(auctionIds(response.auctions())).containsExactly(1002L, 1003L, 1001L);
    }

    @Test
    @DisplayName("한 회원이 1분에 5번을 넘게 부르면 recommend005로 거절하고 LLM을 부르지 않는다")
    void search_sixthCallWithinAMinute_isRejected() {
        fakeChatModel.respondConditions(conditions(true, null, null, null, null, "\"레이싱\""));
        for (int i = 0; i < 5; i++) {
            search("레이싱");
        }

        assertThatThrownBy(() -> search("레이싱"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SEARCH_TOO_MANY_REQUESTS);
        assertThat(fakeChatModel.conditionPrompts()).hasSize(5);
    }

    @Test
    @DisplayName("질문이 비어 있거나 100자를 넘으면 COMMON001로 거절하고 LLM을 부르지 않는다")
    void search_invalidQuery_isRejected() {
        assertThatThrownBy(() -> search("   "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> search("가".repeat(101)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> recommendFacade.searchAuctions(memberId, new SearchRequestDto(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(fakeChatModel.conditionPrompts()).isEmpty();

        assertThat(search(" " + "가".repeat(100) + " ").auctions()).isNotEmpty();
    }

    @Test
    @DisplayName("조건 정하기 호출의 스키마에는 삭제되지 않은 상품의 카테고리와 제조사 값이 enum으로 들어간다")
    void search_schemaUsesStoredValues() {
        recommendKafkaListener.handle(new ProductChangedEvent(
                106L, DELETED, 0L, SELLER_ID, "상품106", "ETC", "SEGA", null,
                START_PRICE, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)
        ));

        search("레이싱");

        Prompt prompt = fakeChatModel.conditionPrompts().getFirst();

        assertThat(prompt.getSystemMessage().getText()).contains("카테고리 값 목록: [ACCESSORY, GAME]", "제조사 값 목록: [NINTENDO, SONY]");
        assertThat(((org.springframework.ai.openai.OpenAiChatOptions) prompt.getOptions()).getResponseFormat().getJsonSchema())
                .contains("\"enum\":[\"ACCESSORY\",\"GAME\"]", "\"enum\":[\"NINTENDO\",\"SONY\"]");
    }

    private SearchResponseDto search(String question) {
        return recommendFacade.searchAuctions(memberId, new SearchRequestDto(question, null));
    }

    private static String conditions(boolean recommendation, Long maxPrice, Long minPrice, String category, String manufacturer, String keywords) {
        return "{\"recommendation\":" + recommendation
                + ",\"maxPrice\":" + maxPrice
                + ",\"minPrice\":" + minPrice
                + ",\"category\":" + (category == null ? "null" : "\"" + category + "\"")
                + ",\"manufacturer\":" + (manufacturer == null ? "null" : "\"" + manufacturer + "\"")
                + ",\"keywords\":[" + keywords + "]}";
    }

    private static String userText(Prompt prompt) {
        return prompt.getUserMessage().getText();
    }

    private void readyAuction(long productId, long auctionId, double degrees, String category, String manufacturer) {
        recommendKafkaListener.handle(new ProductChangedEvent(
                productId, CREATED, 0L, SELLER_ID, "상품" + productId, category, manufacturer, null,
                START_PRICE, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)
        ));
        recommendKafkaListener.handle(new AuctionStartedEvent(
                auctionId, productId, START_PRICE, 50000L, now.minusDays(1), now.plusDays(1), "ENGLISH"));
        jdbcTemplate.update(
                "update product_embedding set status = 'READY', model = ?, embedding = CAST(? AS vector) where id = ?",
                model, vector(degrees), productId);
    }

    private void price(long auctionId, long currentPrice) {
        jdbcTemplate.update("update recommend_auction set current_price = ? where id = ?", currentPrice, auctionId);
    }

    private void bidders(long auctionId, long productId, long... bidderIds) {
        for (long bidderId : bidderIds) {
            jdbcTemplate.update(
                    "insert into recommend_member (id, uuid, nickname, created_at, updated_at) values (?, ?, ?, now(), now())",
                    bidderId, UUID.randomUUID(), "회원" + bidderId);
            jdbcTemplate.update(
                    "insert into interaction (member_id, auction_id, product_id, type, active, last_occurred_at, created_at, updated_at) values (?, ?, ?, 'BID', true, now(), now(), now())",
                    bidderId, auctionId, productId);
        }
    }

    private void clean() {
        interactionLogRepository.deleteAll();
        interactionRepository.deleteAll();
        recommendAuctionRepository.deleteAll();
        recommendMemberRepository.deleteAll();
        productEmbeddingRepository.deleteAll();
        recommendProductRepository.deleteAll();
    }

    private static String vector(double degrees) {
        double radians = Math.toRadians(degrees);
        return Stream.concat(
                        Stream.of(Math.cos(radians), Math.sin(radians)),
                        Stream.generate(() -> 0.0).limit(DIMENSIONS - 2))
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static List<Long> auctionIds(List<AuctionResponseDto> result) {
        return result.stream().map(AuctionResponseDto::auctionId).toList();
    }
}
