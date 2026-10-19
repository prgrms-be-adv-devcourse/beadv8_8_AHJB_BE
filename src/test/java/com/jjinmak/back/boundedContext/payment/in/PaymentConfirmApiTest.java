package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.app.ChargeFailureRecorder;
import com.jjinmak.back.boundedContext.payment.app.PaymentFacade;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.boundedContext.payment.exception.PgApiException;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.HoldingRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.boundedContext.payment.out.TossPaymentsClient;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.boundedContext.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.security.JwtTokenProvider;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.PG_APPROVE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/payments/confirm — 클라이언트가 직접 호출하는 주문 결제 승인.
 * 토스 호출은 모킹. 승인 실패 기록(REQUIRES_NEW)은 테스트 트랜잭션 밖이라 모킹.
 *
 * 시나리오: 구매자 예치금 12_000, 주문 총액 30_000 (주문 71: 22_000 / 주문 72: 8_000) → 부족분 18_000 충전 요청(REQUESTED) 상태에서 시작
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class PaymentConfirmApiTest {

    private static final Long BUYER_ID = 9_301L;
    private static final Long SELLER_ID = 9_302L;
    private static final Long OTHER_ID = 9_303L;
    private static final Long ORDER_GROUP_ID = 7_501L;
    private static final String PAYMENT_KEY = "pk_test_confirm";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ApplicationEvents events;
    @Autowired PaymentFacade paymentFacade;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired HoldingRepository holdingRepository;

    @MockitoBean TossPaymentsClient toss;
    @MockitoBean ChargeFailureRecorder failureRecorder;
    @MockitoBean SettlementEventListener settlementEventListener;

    private Wallet buyerWallet;
    private Payment payment;
    private Charge charge;

    @BeforeEach
    void setUp() {
        buyerWallet = walletOf(BUYER_ID, 12_000L);
        walletOf(SELLER_ID, 0L);
        walletOf(OTHER_ID, 0L);

        payment = Payment.create(ORDER_GROUP_ID, BUYER_ID, buyerWallet, 30_000L);
        payment.addItem(71L, SELLER_ID, 22_000L, 0L);
        payment.addItem(72L, SELLER_ID, 8_000L, 0L);
        payment.validateItems();
        payment.decideCharge(buyerWallet.getBalance());             // 부족분 18_000 → CHARGING
        payment = paymentRepository.save(payment);
        charge = chargeRepository.save(Charge.requestForPayment(buyerWallet, payment));
    }

    private Wallet walletOf(Long memberId, long balance) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        Wallet wallet = Wallet.createUserWallet(member);
        if (balance > 0) wallet.deposit(balance);
        return walletRepository.save(wallet);
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }

    private String body(String pgOrderId, String paymentKey, long amount) {
        return """
                {"pgOrderId":"%s","paymentKey":"%s","amount":%d}
                """.formatted(pgOrderId, paymentKey, amount);
    }

    private TossPaymentResponse tossDone(String pgOrderId, long amount) {
        return new TossPaymentResponse(PAYMENT_KEY, pgOrderId, "찐막 주문 결제 (부족분 충전)", "DONE", amount, amount,
                "카드", "2026-10-19T10:00:00+09:00", "2026-10-19T10:00:05+09:00", "tx_1", null,
                new TossPaymentResponse.Receipt("https://receipt/" + PAYMENT_KEY));
    }

    private long paidEvents() {
        return events.stream(PaymentPaidEvent.class).count();
    }

    // ===== 정상 =====

    @Test
    @DisplayName("승인 성공: 토스 승인 → 충전 → 홀딩 → PAID, 응답에 주문 그룹 id, 결제 완료 이벤트 1건")
    void confirm_success() throws Exception {
        when(toss.confirm(eq(PAYMENT_KEY), eq(charge.getPgOrderId()), eq(18_000L), anyString()))
                .thenReturn(tossDone(charge.getPgOrderId(), 18_000L));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(charge.getPgOrderId(), PAYMENT_KEY, 18_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.orderGroupId").value(ORDER_GROUP_ID))
                .andExpect(jsonPath("$.data.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.data.totalPrice").value(30_000))
                .andExpect(jsonPath("$.data.walletUsedAmount").value(12_000))
                .andExpect(jsonPath("$.data.chargeAmount").value(18_000))
                .andExpect(jsonPath("$.data.paidAt").exists());

        assertThat(buyerWallet.getBalance()).isZero();                       // 12_000 + 18_000 − 30_000
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
        assertThat(charge.getReceiptUrl()).isEqualTo("https://receipt/" + PAYMENT_KEY);
        assertThat(holdingRepository.existsByPaymentId(payment.getId())).isTrue();

        List<PaymentPaidEvent> published = events.stream(PaymentPaidEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().orderGroupId()).isEqualTo(ORDER_GROUP_ID);
        assertThat(published.getFirst().paymentId()).isEqualTo(payment.getId());
        assertThat(published.getFirst().buyerId()).isEqualTo(BUYER_ID);
        assertThat(published.getFirst().totalPrice()).isEqualTo(30_000L);
        assertThat(published.getFirst().eventId()).isNotBlank();
    }

    @Test
    @DisplayName("중복 호출(새로고침): 두 번째는 토스를 다시 부르지 않고 PAID 를 그대로 돌려주며 이벤트도 다시 발행하지 않는다")
    void confirm_idempotent() throws Exception {
        when(toss.confirm(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(tossDone(charge.getPgOrderId(), 18_000L));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/payments/confirm")
                            .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(charge.getPgOrderId(), PAYMENT_KEY, 18_000L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAID"));
        }

        verify(toss, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(paidEvents()).isEqualTo(1);
        assertThat(buyerWallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("전액 예치금 결제(requestPayment 에서 바로 PAID)도 같은 결제 완료 이벤트를 발행한다")
    void requestPayment_walletOnly_publishesSameEvent() {
        buyerWallet.deposit(50_000L);
        PaymentRequestDto dto = new PaymentRequestDto(7_502L, BUYER_ID, 5_000L,
                List.of(new PaymentRequestDto.Item(73L, SELLER_ID, 5_000L, 0L)));

        PaymentResultDto result = paymentFacade.requestPayment(dto);

        assertThat(result.isPaid()).isTrue();
        List<PaymentPaidEvent> published = events.stream(PaymentPaidEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().orderGroupId()).isEqualTo(7_502L);
        assertThat(published.getFirst().paymentId()).isEqualTo(result.paymentId());
    }

    // ===== 실패 =====

    @Test
    @DisplayName("다른 회원의 충전 요청으로 승인 → 403 CHARGE007, retryable=false, 토스 호출 없음")
    void confirm_otherMember_403() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(charge.getPgOrderId(), PAYMENT_KEY, 18_000L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CHARGE007"))
                .andExpect(jsonPath("$.retryable").value(false));

        verify(toss, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(paidEvents()).isZero();
    }

    @Test
    @DisplayName("금액 불일치 → 400 CHARGE003, retryable=false, 토스 호출 없음")
    void confirm_amountMismatch_400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(charge.getPgOrderId(), PAYMENT_KEY, 17_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHARGE003"))
                .andExpect(jsonPath("$.retryable").value(false));

        verify(toss, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.REQUESTED);
    }

    @Test
    @DisplayName("존재하지 않는 pgOrderId → 404 CHARGE001")
    void confirm_unknownPgOrderId_404() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CHG-unknown", PAYMENT_KEY, 18_000L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CHARGE001"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    @DisplayName("수동 충전 요청을 주문 승인 API 로 보냄 → 400 CHARGE008")
    void confirm_manualCharge_400() throws Exception {
        Charge manual = chargeRepository.save(Charge.requestManual(buyerWallet, BUYER_ID, 10_000L));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(manual.getPgOrderId(), PAYMENT_KEY, 10_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHARGE008"));

        verify(toss, never()).confirm(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("이미 닫힌(ABORTED) 충전 요청 → 400 CHARGE004, retryable=true (다시 주문하기로 새 충전 요청)")
    void confirm_abortedCharge_400_retryable() throws Exception {
        charge.markAborted(java.time.LocalDateTime.now(), "PAY_PROCESS_CANCELED", "사용자 취소");

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(charge.getPgOrderId(), PAYMENT_KEY, 18_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHARGE004"))
                .andExpect(jsonPath("$.retryable").value(true));

        verify(toss, never()).confirm(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("토스 거절 → 502 PG001, retryable=true, 토스 코드·메시지 포함, 실패 사유 기록, 돈·홀딩·이벤트 없음")
    void confirm_tossRejected_502_retryable() throws Exception {
        when(toss.confirm(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new PgApiException(PG_APPROVE_FAILED, "REJECT_CARD_COMPANY", "카드사에서 거절했습니다."));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(charge.getPgOrderId(), PAYMENT_KEY, 18_000L)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PG001"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.pgCode").value("REJECT_CARD_COMPANY"))
                .andExpect(jsonPath("$.pgMessage").value("카드사에서 거절했습니다."));

        verify(failureRecorder).recordAborted(eq(charge.getId()), eq("REJECT_CARD_COMPANY"), anyString());
        assertThat(buyerWallet.getBalance()).isEqualTo(12_000L);
        assertThat(holdingRepository.existsByPaymentId(payment.getId())).isFalse();
        assertThat(paidEvents()).isZero();
    }

    @Test
    @DisplayName("토큰 없이 호출 → 401")
    void confirm_withoutToken_401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(charge.getPgOrderId(), PAYMENT_KEY, 18_000L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }
}
