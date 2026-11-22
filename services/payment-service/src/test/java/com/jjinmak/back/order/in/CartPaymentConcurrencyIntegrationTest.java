package com.jjinmak.back.order.in;

import com.jayway.jsonpath.JsonPath;
import com.jjinmak.back.order.domain.CartItem;
import com.jjinmak.back.order.domain.OrderMember;
import com.jjinmak.back.order.out.repository.CartItemRepository;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class CartPaymentConcurrencyIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long WINNER_ID = 101L;
    private static final long SELLER_A_ID = 102L;
    private static final long SELLER_B_ID = 103L;
    private static final long BALANCE = 1_000_000L;

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member, "
            + "orders, order_group, cart_item, order_outbox, order_won_auction, order_member";

    @MockitoBean
    OrderScheduler scheduledOrderScheduler;

    @Autowired WebApplicationContext context;
    @Autowired OrderMemberRepository orderMemberRepository;
    @Autowired CartItemRepository cartItemRepository;
    @MockitoSpyBean OrderGroupRepository orderGroupRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @PersistenceContext EntityManager entityManager;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new HeaderAuthenticationFilter())
                .build();
        resetAndSeed();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        resetDatabase();
    }

    @Test
    @DisplayName("같은 상품으로 결제 요청이 동시에 4번 와도 주문 그룹은 하나만 만들어지고, 네 요청 모두 그 그룹으로 결제 완료 응답을 받으며 예치금은 한 번만 빠진다")
    void concurrentSameProducts_makeOneGroupAndOnePayment() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(4);
        doAnswer(invocation -> {
            Object found = mockingDetails(orderGroupRepository).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            try {
                barrier.await(2, TimeUnit.SECONDS);
            } catch (TimeoutException | BrokenBarrierException ignored) {
            }
            return found;
        }).when(orderGroupRepository).findWaitingByWinnerAndLifeTime(any(), any());

        List<Response> responses = concurrently("[20, 21]", "[20, 21]", "[20, 21]", "[20, 21]");

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.isPaid()).isTrue();
        });
        List<Long> groupIds = groupIds();
        assertThat(groupIds).hasSize(1);
        assertThat(responses).extracting(Response::groupId).containsOnly(groupIds.getFirst());
        assertThat(paymentStatuses()).containsExactly("PAID");
        assertThat(balance()).isEqualTo(BALANCE - 76_000L);
    }

    @Test
    @DisplayName("일부만 겹치는 결제 요청([20, 21]과 [20])이 동시에 오면 한쪽만 결제되고 다른 쪽은 ORDER_STATE_BAD_REQUEST로 거절되며, 주문 그룹 없이 남는 결제가 생기지 않는다")
    void concurrentOverlappingProducts_payOnceWithoutOrphanPayment() throws Exception {
        for (int round = 0; round < 3; round++) {
            if (round > 0) {
                resetAndSeed();
            }

            List<Response> responses = concurrently("[20, 21]", "[20]");

            assertThat(responses).extracting(Response::status).containsExactlyInAnyOrder(200, 400);
            assertThat(responses).filteredOn(response -> response.status() == 400)
                    .extracting(Response::errorCode).containsExactly("ORDER003");
            assertThat(groupIds()).hasSize(1);
            assertThat(paymentStatuses()).containsExactly("PAID");
            assertThat(paidPaymentsWithoutGroup()).isZero();
        }
    }

    @Test
    @DisplayName("결제창이 열린 채 10초 넘게 지난 [20] 그룹이 있을 때 [20, 21]을 결제하면, 이전 그룹의 결제가 닫히고(결제 FAILED, 결제창 ABORTED) 그룹이 지워지며 삭제 이벤트가 아웃박스에 남고, 새 그룹이 결제된다")
    void overlappingOldGroup_isClosedAndReplaced() throws Exception {
        Response first = pay("[20]", "CARD");
        ageAllGroups();

        Response second = pay("[20, 21]", null);

        assertThat(first.isPaid()).isFalse();
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.isPaid()).isTrue();
        assertThat(groupIds()).containsExactly(second.groupId());
        assertThat(paymentStatusOf(first.groupId())).isEqualTo("FAILED");
        assertThat(chargeStatusesOf(first.groupId())).containsExactly("ABORTED");
        assertThat(outbox()).containsExactly("order.group-deleted:" + first.groupId());
        assertThat(paymentStatusOf(second.groupId())).isEqualTo("PAID");
        assertThat(balance()).isEqualTo(BALANCE - 76_000L);
    }

    @Test
    @DisplayName("[20] 그룹의 결제창을 연 직후 [20, 21]을 결제하면 ORDER_STATE_BAD_REQUEST로 거절되고, 이전 그룹과 결제, 결제창은 그대로 남는다")
    void overlappingRecentGroup_isRefused() throws Exception {
        Response first = pay("[20]", "CARD");

        Response second = pay("[20, 21]", null);

        assertThat(second.status()).isEqualTo(400);
        assertThat(second.errorCode()).isEqualTo("ORDER003");
        assertThat(groupIds()).containsExactly(first.groupId());
        assertThat(paymentStatusOf(first.groupId())).isEqualTo("CHARGING");
        assertThat(chargeStatusesOf(first.groupId())).containsExactly("REQUESTED");
        assertThat(outbox()).isEmpty();
        assertThat(balance()).isEqualTo(BALANCE);
    }

    @Test
    @DisplayName("lifeTime이 지난 같은 조합의 그룹이 아직 지워지지 않았을 때 다시 결제하면, 이전 그룹의 결제를 닫고 그룹을 지운 뒤 새 그룹으로 결제한다")
    void expiredSameProducts_areClosedAndRecreated() throws Exception {
        Response first = pay("[20, 21]", "CARD");
        expireAllGroups();

        Response second = pay("[20, 21]", null);

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.isPaid()).isTrue();
        assertThat(second.groupId()).isNotEqualTo(first.groupId());
        assertThat(groupIds()).containsExactly(second.groupId());
        assertThat(paymentStatusOf(first.groupId())).isEqualTo("FAILED");
        assertThat(chargeStatusesOf(first.groupId())).containsExactly("ABORTED");
        assertThat(balance()).isEqualTo(BALANCE - 76_000L);
    }

    @Test
    @DisplayName("예치금 결제는 끝났지만 주문에는 아직 반영되지 않은 [20] 그룹과 겹치게 [20, 21]을 결제하면 ORDER_STATE_BAD_REQUEST로 거절되고, 결제는 한 번만 일어난다")
    void overlappingPaidGroup_isRefused() throws Exception {
        Response first = pay("[20]", null);
        ageAllGroups();

        Response second = pay("[20, 21]", null);

        assertThat(first.isPaid()).isTrue();
        assertThat(second.status()).isEqualTo(400);
        assertThat(second.errorCode()).isEqualTo("ORDER003");
        assertThat(groupIds()).containsExactly(first.groupId());
        assertThat(paymentStatuses()).containsExactly("PAID");
        assertThat(balance()).isEqualTo(BALANCE - 33_000L);
    }

    private record Response(int status, String body) {

        Long groupId() {
            return ((Number) JsonPath.read(body, "$.data.orderGroupId")).longValue();
        }

        boolean isPaid() {
            return Boolean.TRUE.equals(JsonPath.read(body, "$.data.isPaid"));
        }

        String errorCode() {
            return JsonPath.read(body, "$.errorCode");
        }
    }

    private Response pay(String productIds, String paymentMethod) throws Exception {
        String method = paymentMethod == null ? "" : ", \"paymentMethod\": \"" + paymentMethod + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, WINNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": " + productIds + ", \"pointUseAmount\": 0" + method + "}"))
                .andReturn();
        return new Response(result.getResponse().getStatus(), result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<Response> concurrently(String... productIdsList) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(productIdsList.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Response>> futures = new ArrayList<>();
            for (String productIds : productIdsList) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return pay(productIds, null);
                }));
            }
            start.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private void ageAllGroups() {
        jdbcTemplate.update("update order_group set updated_at = ?", LocalDateTime.now().minusMinutes(1));
    }

    private void expireAllGroups() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update order_group set life_time = ?, updated_at = ?", now.minusMinutes(1), now.minusMinutes(61));
    }

    private List<Long> groupIds() {
        return jdbcTemplate.queryForList("select id from order_group order by id", Long.class);
    }

    private List<String> paymentStatuses() {
        return jdbcTemplate.queryForList("select status from payment order by id", String.class);
    }

    private String paymentStatusOf(Long groupId) {
        return jdbcTemplate.queryForObject("select status from payment where order_group_id = ?", String.class, groupId);
    }

    private List<String> chargeStatusesOf(Long groupId) {
        return jdbcTemplate.queryForList(
                "select c.status from charge c join payment p on p.id = c.payment_id where p.order_group_id = ? order by c.id",
                String.class, groupId);
    }

    private List<String> outbox() {
        return jdbcTemplate.queryForList("select topic || ':' || message_key from order_outbox order by id", String.class);
    }

    private long paidPaymentsWithoutGroup() {
        return jdbcTemplate.queryForObject(
                "select count(*) from payment p where p.status = 'PAID' and not exists (select 1 from order_group g where g.id = p.order_group_id)",
                Long.class);
    }

    private long balance() {
        return walletRepository.findByHolderId(WINNER_ID).orElseThrow().getBalance();
    }

    private void resetAndSeed() {
        resetDatabase();
        OrderMember winner = createMember(WINNER_ID, "낙찰자");
        OrderMember sellerA = createMember(SELLER_A_ID, "판매자A");
        OrderMember sellerB = createMember(SELLER_B_ID, "판매자B");
        cartItemRepository.save(new CartItem(winner, sellerA, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now()));
        cartItemRepository.save(new CartItem(winner, sellerB, 11L, 21L, 40_000L, 3_000L, LocalDateTime.now()));
        givenBalance(BALANCE);
    }

    private void givenBalance(long amount) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByHolderId(WINNER_ID).orElseThrow();
            Charge charge = chargeRepository.save(Charge.requestManual(wallet, WINNER_ID, amount));
            ledgerRecorder.deposit(wallet, CHARGE, amount, PAYMENT, charge.getId());
            LocalDateTime now = LocalDateTime.now();
            charge.markCharged("payment-key-" + charge.getId(), now, now, "계좌이체", null);
        });
    }

    private OrderMember createMember(long memberId, String name) {
        transactionTemplate.executeWithoutResult(status -> {
            WalletMember walletMember = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
            walletRepository.save(Wallet.createUserWallet(walletMember));
        });
        return orderMemberRepository.save(new OrderMember(memberId, UUID.randomUUID(), name));
    }

    private void resetDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("truncate table " + TABLES + " restart identity cascade").executeUpdate();
            walletRepository.save(Wallet.createSystemWallet(WalletType.FEE));
            walletRepository.save(Wallet.createSystemWallet(WalletType.ESCROW));
        });
    }
}
