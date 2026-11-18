package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.support.PaymentApiTestData;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.jjinmak.back.boundedContext.payment.support.PaymentApiTestData.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/wallet 조회 API 통합 테스트.
 * 데이터 구성은 {@link PaymentApiTestData} 참고.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PaymentApiTestData.class)
class WalletApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    PaymentApiTestData data;

    @MockitoBean
    SettlementEventListener settlementEventListener;

    @BeforeEach
    void setUp() {
        data.seed();
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }

    // ===== 인증 =====

    @Test
    @DisplayName("토큰 없이 잔액 조회 → 401")
    void balance_withoutToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    // ===== 잔액 =====

    @Test
    @DisplayName("내 잔액 조회: 예치금과 정산금이 따로 내려온다")
    void balance() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20_000))
                .andExpect(jsonPath("$.data.settlementBalance").value(0));
    }

    @Test
    @DisplayName("판매자 잔액 조회: 정산금은 예치금에 섞이지 않는다")
    void balance_seller() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(SELLER_A_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(3_000))
                .andExpect(jsonPath("$.data.settlementBalance").value(9_000));
    }

    // ===== 원장 =====

    @Test
    @DisplayName("원장 조회: 잔액과 함께 최신순으로 내려온다")
    void ledger_latestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20_000))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].account").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.content[0].type").value("HOLD_PAY"))
                .andExpect(jsonPath("$.data.content[0].amount").value(-22_000))
                .andExpect(jsonPath("$.data.content[0].referenceType").value("ORDER"))
                .andExpect(jsonPath("$.data.content[0].referenceId").value(10))
                .andExpect(jsonPath("$.data.content[1].type").value("CHARGE"))
                .andExpect(jsonPath("$.data.content[1].amount").value(10_000))
                .andExpect(jsonPath("$.data.content[1].referenceType").value("PAYMENT"));
    }

    @Test
    @DisplayName("원장 조회: type 으로 필터링")
    void ledger_filterByType() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/ledger")
                        .param("type", "CHARGE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("CHARGE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(10_000));
    }

    @Test
    @DisplayName("원장 조회: 페이징 (size=1 이면 hasNext=true)")
    void ledger_paging() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/ledger")
                        .param("page", "0").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("원장 조회: 다른 회원의 기록은 섞이지 않고, balanceAfter 는 각 주머니 기준이다")
    void ledger_onlyMine() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/ledger")
                        .header(HttpHeaders.AUTHORIZATION, bearer(SELLER_A_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(3_000))
                .andExpect(jsonPath("$.data.settlementBalance").value(9_000))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].account").value("SETTLEMENT"))
                .andExpect(jsonPath("$.data.content[0].type").value("SETTLEMENT_RECEIVE"))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(9_000))
                .andExpect(jsonPath("$.data.content[1].account").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.content[1].type").value("CHARGE"))
                .andExpect(jsonPath("$.data.content[1].balanceAfter").value(3_000));
    }

    @Test
    @DisplayName("원장 조회: account 로 예치금/정산금 기록을 나눠 볼 수 있다")
    void ledger_filterByAccount() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/ledger")
                        .param("account", "SETTLEMENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(SELLER_A_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].account").value("SETTLEMENT"))
                .andExpect(jsonPath("$.data.content[0].amount").value(9_000));

        mockMvc.perform(get("/api/v1/wallet/me/ledger")
                        .param("account", "DEPOSIT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(SELLER_A_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("CHARGE"));
    }

    // ===== 홀딩 (구매자) =====

    @Test
    @DisplayName("구매자 홀딩 조회: 기본 role=BUYER, 합계와 상대방(판매자) id 가 내려온다")
    void holdings_buyer() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/holdings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("BUYER"))
                .andExpect(jsonPath("$.data.heldTotal").value(30_000))          // H1 22_000 + H2 8_000
                .andExpect(jsonPath("$.data.settleableTotal").value(8_000))     // H2 만 CONFIRMED
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].orderId").value(11))     // 최신순
                .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.content[0].counterpartyId").value(SELLER_B_ID))
                .andExpect(jsonPath("$.data.content[1].orderId").value(10))
                .andExpect(jsonPath("$.data.content[1].status").value("HELD"))
                .andExpect(jsonPath("$.data.content[1].counterpartyId").value(SELLER_A_ID))
                .andExpect(jsonPath("$.data.content[1].amount").value(22_000))
                .andExpect(jsonPath("$.data.content[1].feeAmount").value(2_000))
                .andExpect(jsonPath("$.data.content[1].settlementAmount").value(20_000));
    }

    @Test
    @DisplayName("구매자 홀딩 조회: status 로 필터링")
    void holdings_buyer_filterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/holdings")
                        .param("status", "HELD")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderId").value(10))
                .andExpect(jsonPath("$.data.content[0].status").value("HELD"));
    }

    // ===== 홀딩 (판매자) =====

    @Test
    @DisplayName("판매자 홀딩 조회: 받을 돈(수수료 제외) 합계와 상대방(구매자) id 가 내려온다")
    void holdings_seller() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/holdings")
                        .param("role", "SELLER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(SELLER_A_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SELLER"))
                .andExpect(jsonPath("$.data.heldTotal").value(20_000))          // H1 만 미정산 (22_000 - 2_000)
                .andExpect(jsonPath("$.data.settleableTotal").value(0))         // CONFIRMED 없음
                .andExpect(jsonPath("$.data.totalElements").value(2))           // H1(HELD) + H3(RELEASED)
                .andExpect(jsonPath("$.data.content[0].orderId").value(13))
                .andExpect(jsonPath("$.data.content[0].status").value("RELEASED"))
                .andExpect(jsonPath("$.data.content[0].counterpartyId").value(OTHER_ID))
                .andExpect(jsonPath("$.data.content[1].orderId").value(10))
                .andExpect(jsonPath("$.data.content[1].counterpartyId").value(BUYER_ID));
    }

    @Test
    @DisplayName("판매자 홀딩 조회: 구매 확정 건만 보면 정산 대기 금액과 일치한다")
    void holdings_seller_confirmedOnly() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/holdings")
                        .param("role", "SELLER").param("status", "CONFIRMED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(SELLER_B_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heldTotal").value(7_300))           // H2 8_000 - 700
                .andExpect(jsonPath("$.data.settleableTotal").value(7_300))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderId").value(11))
                .andExpect(jsonPath("$.data.content[0].settlementAmount").value(7_300));
    }

    @Test
    @DisplayName("홀딩이 없는 회원은 빈 목록과 0 합계")
    void holdings_empty() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/me/holdings")
                        .param("role", "SELLER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heldTotal").value(0))
                .andExpect(jsonPath("$.data.settleableTotal").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }
}
