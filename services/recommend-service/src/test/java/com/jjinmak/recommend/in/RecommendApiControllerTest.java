package com.jjinmak.recommend.in;

import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.out.repository.InteractionLogRepository;
import com.jjinmak.recommend.out.repository.InteractionRepository;
import com.jjinmak.recommend.out.repository.ProductEmbeddingRepository;
import com.jjinmak.recommend.out.repository.RecommendAuctionRepository;
import com.jjinmak.recommend.out.repository.RecommendMemberRepository;
import com.jjinmak.recommend.out.repository.RecommendProductRepository;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendApiControllerTest extends RecommendIntegrationTestSupport {

    static final long SELLER_ID = 9L;
    static final long START_PRICE = 10000L;
    static final String CATEGORY = "ACCESSORY";
    static final int DIMENSIONS = 1536;

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    RecommendKafkaListener recommendKafkaListener;

    @Autowired
    RecommendAuctionRepository recommendAuctionRepository;

    @Autowired
    RecommendProductRepository recommendProductRepository;

    @Autowired
    RecommendMemberRepository recommendMemberRepository;

    @Autowired
    InteractionRepository interactionRepository;

    @Autowired
    InteractionLogRepository interactionLogRepository;

    @Autowired
    ProductEmbeddingRepository productEmbeddingRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.openai.embedding.model}")
    String model;

    MockMvc mockMvc;

    LocalDateTime now;

    @BeforeEach
    void setUp() {
        clean();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        readyAuction(101L, 1001L, 0, now.plusHours(30));
        readyAuction(102L, 1002L, 10, now.plusHours(40));
        readyAuction(103L, 1003L, 40, now.plusHours(50));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("GET /api/v1/recommend/{auctionId}는 비슷한 경매 목록을 공통 응답 형식의 data로 돌려준다")
    void similar_returnsListInRsData() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/{auctionId}", 1001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data[*].auctionId").value(contains(1002, 1003)))
                .andExpect(jsonPath("$.data[0].productId").value(102))
                .andExpect(jsonPath("$.data[0].category").value(CATEGORY))
                .andExpect(jsonPath("$.data[0].currentPrice").value(START_PRICE))
                .andExpect(jsonPath("$.data[0].endAt").exists());
    }

    @Test
    @DisplayName("추천 쪽에 없는 경매로 요청하면 404와 recommend002 에러 응답을 돌려준다")
    void similar_unknownAuction_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/{auctionId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.errorCode").value("recommend002"));
    }

    @Test
    @DisplayName("GET /api/v1/recommend/popular는 경매 id 경로가 아니라 인기 경매 목록으로 연결된다")
    void popular_returnsPopularListInRsData() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data[*].auctionId").value(contains(1001, 1002, 1003)));
    }

    private void readyAuction(long productId, long auctionId, double degrees, LocalDateTime endAt) {
        recommendKafkaListener.handle(new ProductChangedEvent(
                productId, CREATED, 0L, SELLER_ID, "상품" + productId, CATEGORY, "SONY", null,
                START_PRICE, null, LocalDateTime.of(2026, 11, 12, 10, 0), null, LocalDateTime.of(2026, 11, 10, 9, 0)
        ));
        recommendKafkaListener.handle(new AuctionStartedEvent(auctionId, productId, START_PRICE, 50000L, now.minusDays(1), endAt, "ENGLISH"));
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
