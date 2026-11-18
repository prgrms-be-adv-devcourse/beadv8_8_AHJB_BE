package com.jjinmak.back.boundedContext.payment.in;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import com.jjinmak.back.boundedContext.payment.out.WithdrawalRepository;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/wallet/me/withdrawals — 예치금 전액 출금(명목상)과 출금 내역.
 *
 * 시나리오: 예치금 15_000 = 충전 C1 10_000(CHARGED, 미사용) + 환불금 5_000, 정산금 7_000
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalApiTest {

    private static final Long HOLDER_ID = 9_401L;
    private static final Long OTHER_ID = 9_402L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired WalletTransactionRepository walletTransactionRepository;
    @Autowired WithdrawalRepository withdrawalRepository;

    @MockitoBean SettlementEventListener settlementEventListener;

    private Wallet wallet;
    private Charge c1;

    @BeforeEach
    void setUp() {
        wallet = walletOf(HOLDER_ID);
        walletOf(OTHER_ID);

        c1 = Charge.requestManual(wallet, HOLDER_ID, 10_000L);
        c1.markCharged("pk-c1", T0, T0);
        c1 = chargeRepository.save(c1);
        wallet.deposit(10_000L);
        walletTransactionRepository.save(WalletTransaction.of(
                wallet, WalletTransactionType.CHARGE, 10_000L, "g1", ReferenceType.PAYMENT, c1.getId()));

        wallet.deposit(5_000L);                 // 환불금 (충전 아님)
        wallet.depositSettlement(7_000L);       // 정산금
    }

    private Wallet walletOf(Long memberId) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        return walletRepository.save(Wallet.createUserWallet(member));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }

    @Test
    @DisplayName("전액 출금: 예치금이 0 이 되고 정산금은 그대로, 출금 기록과 원장이 남는다")
    void withdrawAll() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.withdrawalId").exists())
                .andExpect(jsonPath("$.data.amount").value(15_000))
                .andExpect(jsonPath("$.data.account").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.requestedAt").exists())
                .andExpect(jsonPath("$.data.completedAt").exists());

        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getSettlementBalance()).isEqualTo(7_000L);

        // 원장: 예치금 주머니에 WITHDRAWAL -15_000, 기록 후 잔액 0
        mockMvc.perform(get("/api/v1/wallet/me/ledger")
                        .param("type", "WITHDRAWAL")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.settlementBalance").value(7_000))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].account").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.content[0].amount").value(-15_000))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(0))
                .andExpect(jsonPath("$.data.content[0].referenceType").value("WITHDRAWAL"));
    }

    @Test
    @DisplayName("출금 후에는 이전 충전 건의 미사용 금액이 0 이 되어 취소할 수 없다")
    void withdrawAll_consumesAllCharges() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isCreated());

        assertThat(c1.getRemainingAmount()).isZero();
        assertThat(c1.isCancelable()).isFalse();

        mockMvc.perform(post("/api/v1/payments/charges/{chargeId}/cancel", c1.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHARGE005"));
    }

    @Test
    @DisplayName("잔액이 0 이면 출금할 수 없다 (WALLET601)")
    void withdrawAll_emptyBalance_400() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WALLET601"))
                .andExpect(jsonPath("$.retryable").value(false));

        assertThat(withdrawalRepository.count()).isZero();
    }

    @Test
    @DisplayName("두 번 연속 출금하면 두 번째는 잔액이 0 이라 실패한다")
    void withdrawAll_twice() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WALLET601"));

        assertThat(withdrawalRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("출금 내역: 내 것만 최신순으로")
    void listWithdrawals() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isCreated());
        wallet.deposit(3_000L);                                   // 다시 입금(환불) 후 한 번 더 출금
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(HOLDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].amount").value(3_000))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[1].amount").value(15_000));

        mockMvc.perform(get("/api/v1/wallet/me/withdrawals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("토큰 없이 호출 → 401")
    void withdrawAll_withoutToken_401() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/me/withdrawals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }
}
