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
 * /api/v1/payments 조회 API 통합 테스트 (결제 내역, 충전 내역, 결제 견적).
 * 데이터 구성은 {@link PaymentApiTestData} 참고.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PaymentApiTestData.class)
class PaymentApiControllerTest {

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
    @DisplayName("토큰 없이 결제 내역 조회 → 401")
    void payments_withoutToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    // ===== 결제 내역 =====

    @Test
    @DisplayName("결제 내역 목록: 내 결제만 최신순으로, 예치금 사용액/충전액/주문 수가 내려온다")
    void payments_list() throws Exception {
        mockMvc.perform(get("/api/v1/payments/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                // P2 (최신) : 전액 예치금
                .andExpect(jsonPath("$.data.content[0].paymentId").value(data.p2.getId()))
                .andExpect(jsonPath("$.data.content[0].orderGroupId").value(P2_GROUP))
                .andExpect(jsonPath("$.data.content[0].totalPrice").value(5_000))
                .andExpect(jsonPath("$.data.content[0].walletUsedAmount").value(5_000))
                .andExpect(jsonPath("$.data.content[0].chargeAmount").value(0))
                .andExpect(jsonPath("$.data.content[0].orderCount").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"))
                // P1 : 예치금 12_000 + 충전 18_000
                .andExpect(jsonPath("$.data.content[1].paymentId").value(data.p1.getId()))
                .andExpect(jsonPath("$.data.content[1].totalPrice").value(30_000))
                .andExpect(jsonPath("$.data.content[1].walletUsedAmount").value(12_000))
                .andExpect(jsonPath("$.data.content[1].chargeAmount").value(18_000))
                .andExpect(jsonPath("$.data.content[1].orderCount").value(2))
                .andExpect(jsonPath("$.data.content[1].paidAt").exists());
    }

    @Test
    @DisplayName("결제 상세: 주문 항목과 이 결제를 위해 충전한 내역(영수증 포함)이 내려온다")
    void payment_detail() throws Exception {
        mockMvc.perform(get("/api/v1/payments/me/{paymentId}", data.p1.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(data.p1.getId()))
                .andExpect(jsonPath("$.data.orderGroupId").value(P1_GROUP))
                .andExpect(jsonPath("$.data.totalPrice").value(30_000))
                .andExpect(jsonPath("$.data.walletUsedAmount").value(12_000))
                .andExpect(jsonPath("$.data.chargeAmount").value(18_000))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].orderId").value(10))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(SELLER_A_ID))
                .andExpect(jsonPath("$.data.items[0].winningPrice").value(20_000))
                .andExpect(jsonPath("$.data.items[0].deliveryFee").value(2_000))
                .andExpect(jsonPath("$.data.items[0].amount").value(22_000))
                .andExpect(jsonPath("$.data.items[0].refunded").value(false))
                .andExpect(jsonPath("$.data.items[1].orderId").value(11))
                .andExpect(jsonPath("$.data.charges", hasSize(1)))
                .andExpect(jsonPath("$.data.charges[0].purpose").value("FOR_PAYMENT"))
                .andExpect(jsonPath("$.data.charges[0].status").value("USED"))
                .andExpect(jsonPath("$.data.charges[0].amount").value(18_000))
                .andExpect(jsonPath("$.data.charges[0].cancelable").value(false))
                .andExpect(jsonPath("$.data.charges[0].method").value("카드"))
                .andExpect(jsonPath("$.data.charges[0].receiptUrl").value("https://receipt/pk_p1"));
    }

    @Test
    @DisplayName("결제 상세: 전액 예치금 결제는 충전 내역이 비어 있다")
    void payment_detail_walletOnly() throws Exception {
        mockMvc.perform(get("/api/v1/payments/me/{paymentId}", data.p2.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chargeAmount").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.charges", hasSize(0)));
    }

    @Test
    @DisplayName("결제 상세: 다른 회원의 결제는 404 PAYMENT001")
    void payment_detail_otherMember_404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/me/{paymentId}", data.p3.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT001"));
    }

    @Test
    @DisplayName("결제 상세: 존재하지 않는 결제는 404 PAYMENT001")
    void payment_detail_notFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/me/{paymentId}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT001"));
    }

    // ===== 충전 내역 =====

    @Test
    @DisplayName("충전 내역 목록: 수동 충전만 최신순으로, 결제용 충전은 제외된다")
    void charges_list_manualOnly() throws Exception {
        mockMvc.perform(get("/api/v1/payments/charges/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                // C2 (최신) : 승인 실패
                .andExpect(jsonPath("$.data.content[0].chargeId").value(data.c2.getId()))
                .andExpect(jsonPath("$.data.content[0].purpose").value("MANUAL"))
                .andExpect(jsonPath("$.data.content[0].status").value("ABORTED"))
                .andExpect(jsonPath("$.data.content[0].cancelable").value(false))
                .andExpect(jsonPath("$.data.content[0].failureCode").value("REJECT_CARD_COMPANY"))
                .andExpect(jsonPath("$.data.content[0].failureMessage").value("카드사 거절"))
                // C1 : 충전 완료, 취소 가능
                .andExpect(jsonPath("$.data.content[1].chargeId").value(data.c1.getId()))
                .andExpect(jsonPath("$.data.content[1].amount").value(10_000))
                .andExpect(jsonPath("$.data.content[1].status").value("CHARGED"))
                .andExpect(jsonPath("$.data.content[1].cancelable").value(true))
                .andExpect(jsonPath("$.data.content[1].method").value("간편결제"))
                .andExpect(jsonPath("$.data.content[1].receiptUrl").value("https://receipt/pk_c1"))
                .andExpect(jsonPath("$.data.content[1].approvedAt").exists());
    }

    @Test
    @DisplayName("충전 상세: 내 충전 건은 조회된다")
    void charge_detail() throws Exception {
        mockMvc.perform(get("/api/v1/payments/charges/{chargeId}", data.c1.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chargeId").value(data.c1.getId()))
                .andExpect(jsonPath("$.data.pgOrderId").value(data.c1.getPgOrderId()))
                .andExpect(jsonPath("$.data.amount").value(10_000))
                .andExpect(jsonPath("$.data.status").value("CHARGED"))
                .andExpect(jsonPath("$.data.cancelable").value(true));
    }

    @Test
    @DisplayName("충전 상세: 결제용 충전도 본인 것이면 조회된다")
    void charge_detail_forPayment() throws Exception {
        mockMvc.perform(get("/api/v1/payments/charges/{chargeId}", data.c1ForP1.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("FOR_PAYMENT"))
                .andExpect(jsonPath("$.data.status").value("USED"))
                .andExpect(jsonPath("$.data.usedAt").exists());
    }

    @Test
    @DisplayName("충전 상세: 다른 회원의 충전은 404 CHARGE001")
    void charge_detail_otherMember_404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/charges/{chargeId}", data.c3.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CHARGE001"));
    }

    // ===== 결제 견적 =====

    @Test
    @DisplayName("견적: 잔액이 부족하면 부족분만큼 충전이 필요하다")
    void quote_shortage() throws Exception {
        mockMvc.perform(get("/api/v1/payments/quote")
                        .param("amount", "35000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20_000))
                .andExpect(jsonPath("$.data.amount").value(35_000))
                .andExpect(jsonPath("$.data.walletUseAmount").value(20_000))
                .andExpect(jsonPath("$.data.shortage").value(15_000))
                .andExpect(jsonPath("$.data.needsCharge").value(true));
    }

    @Test
    @DisplayName("견적: 잔액이 충분하면 충전 없이 전액 예치금으로 결제된다")
    void quote_enough() throws Exception {
        mockMvc.perform(get("/api/v1/payments/quote")
                        .param("amount", "20000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.walletUseAmount").value(20_000))
                .andExpect(jsonPath("$.data.shortage").value(0))
                .andExpect(jsonPath("$.data.needsCharge").value(false));
    }

    @Test
    @DisplayName("견적: 금액이 0 이하이면 400 PAYMENT007")
    void quote_invalidAmount_400() throws Exception {
        mockMvc.perform(get("/api/v1/payments/quote")
                        .param("amount", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT007"));
    }
}
