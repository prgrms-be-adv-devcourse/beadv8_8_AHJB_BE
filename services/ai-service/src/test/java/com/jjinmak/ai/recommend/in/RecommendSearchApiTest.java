package com.jjinmak.ai.recommend.in;

import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.ai.recommend.out.repository.InteractionLogRepository;
import com.jjinmak.ai.recommend.out.repository.InteractionRepository;
import com.jjinmak.ai.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.ai.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.ai.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.ai.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.ai.recommend.support.FakeChatModel;
import com.jjinmak.ai.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendSearchApiTest extends RecommendIntegrationTestSupport {

    static final long SELLER_ID = 9L;
    static final long START_PRICE = 10000L;
    static final String CATEGORY = "GAME";
    static final int DIMENSIONS = 1536;
    static long nextMemberId = 200L;

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired RecommendKafkaListener recommendKafkaListener;
    @Autowired RecommendAuctionRepository recommendAuctionRepository;
    @Autowired RecommendProductRepository recommendProductRepository;
    @Autowired RecommendMemberRepository recommendMemberRepository;
    @Autowired InteractionRepository interactionRepository;
    @Autowired InteractionLogRepository interactionLogRepository;
    @Autowired ProductEmbeddingRepository productEmbeddingRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired FakeChatModel fakeChatModel;

    @Value("${spring.ai.openai.embedding.model}")
    String model;

    MockMvc mockMvc;
    LocalDateTime now;
    long memberId;

    @BeforeEach
    void setUp() {
        clean();
        fakeChatModel.reset();
        memberId = nextMemberId++;
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new HeaderAuthenticationFilter())
                .build();
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        readyAuction(101L, 1001L, 120);
        readyAuction(102L, 1002L, 0);
        readyAuction(103L, 1003L, 60);
    }

    @AfterEach
    void tearDown() {
        clean();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/recommend/search는 공통 응답 형식의 data에 새 조건, 요약, 경매 목록을 담아 돌려준다")
    void search_returnsConditionsSummaryAndAuctions() throws Exception {
        fakeChatModel.respondConditions(
                "{\"recommendation\":true,\"maxPrice\":100000,\"minPrice\":null,\"category\":null,\"manufacturer\":null,\"keywords\":[\"스위치\",\"레이싱\"]}");

        mockMvc.perform(post("/api/v1/recommend/search")
                        .header(AuthHeaders.MEMBER_ID, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"10만 원 이하 스위치 레이싱 게임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.conditions.maxPrice").value(100000))
                .andExpect(jsonPath("$.data.conditions.minPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.conditions.keywords").value(contains("스위치", "레이싱")))
                .andExpect(jsonPath("$.data.summary").value(FakeChatModel.DEFAULT_SUMMARY_RESPONSE))
                .andExpect(jsonPath("$.data.auctions[*].auctionId").value(contains(1002, 1003, 1001)))
                .andExpect(jsonPath("$.data.auctions[0].productId").value(102))
                .andExpect(jsonPath("$.data.auctions[0].category").value(CATEGORY))
                .andExpect(jsonPath("$.data.auctions[0].currentPrice").value(START_PRICE))
                .andExpect(jsonPath("$.data.auctions[0].endAt").exists());
    }

    @Test
    @DisplayName("요청의 conditions는 앞 조건으로 LLM에 전달된다")
    void search_passesRequestConditionsToLlm() throws Exception {
        mockMvc.perform(post("/api/v1/recommend/search")
                        .header(AuthHeaders.MEMBER_ID, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"PS5로 바꿔 줘\",\"conditions\":{\"maxPrice\":100000,\"keywords\":[\"스위치\",\"레이싱\"]}}"))
                .andExpect(status().isOk());

        assertThat(fakeChatModel.conditionPrompts().getFirst().getUserMessage().getText())
                .contains("\"maxPrice\":100000", "\"keywords\":[\"스위치\",\"레이싱\"]", "회원의 말: PS5로 바꿔 줘");
    }

    @Test
    @DisplayName("추천 요청이 아니면 요약 자리에 안내 문장, 빈 목록, 앞 조건을 돌려준다")
    void search_nonRecommendation_returnsGuidance() throws Exception {
        fakeChatModel.respondConditions(
                "{\"recommendation\":false,\"maxPrice\":null,\"minPrice\":null,\"category\":null,\"manufacturer\":null,\"keywords\":[]}");

        mockMvc.perform(post("/api/v1/recommend/search")
                        .header(AuthHeaders.MEMBER_ID, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"입찰은 어떻게 해요?\",\"conditions\":{\"keywords\":[\"스위치\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("경매 찾기만 도와드릴 수 있어요. 찾는 상품이나 가격 조건을 말씀해 주세요."))
                .andExpect(jsonPath("$.data.auctions").value(empty()))
                .andExpect(jsonPath("$.data.conditions.keywords").value(contains("스위치")));
    }

    @Test
    @DisplayName("질문이 비어 있으면 400 COMMON001을 돌려준다")
    void search_blankQuery_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/recommend/search")
                        .header(AuthHeaders.MEMBER_ID, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));
    }

    @Test
    @DisplayName("한 회원이 1분에 6번째로 부르면 429 recommend005를 돌려준다")
    void search_sixthCallWithinAMinute_returnsTooManyRequests() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/recommend/search")
                            .header(AuthHeaders.MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"레이싱\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/recommend/search")
                        .header(AuthHeaders.MEMBER_ID, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"레이싱\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("recommend005"));
    }

    private void readyAuction(long productId, long auctionId, double degrees) {
        recommendKafkaListener.handle(new ProductChangedEvent(
                productId, CREATED, 0L, SELLER_ID, "상품" + productId, CATEGORY, "SONY", null,
                START_PRICE, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)
        ));
        recommendKafkaListener.handle(new AuctionStartedEvent(auctionId, productId, START_PRICE, 50000L, now.minusDays(1), now.plusDays(1), "ENGLISH"));
        jdbcTemplate.update(
                "update product_embedding set status = 'READY', model = ?, embedding = CAST(? AS vector) where id = ?",
                model, vector(degrees), productId);
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
}
