package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CartApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    OrderMember winner;
    OrderMember seller;

    Long myItemId;

    @BeforeEach
    void setUp() {
        winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderMember other = orderMemberRepository.save(new OrderMember(3L, UUID.randomUUID(), "다른 낙찰자"));

        myItemId = cartItemRepository.save(new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())).getId();
        cartItemRepository.save(new CartItem(other, seller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now()));
    }

    @Test
    @DisplayName("토큰 없이 장바구니를 조회하면 401")
    void read_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @Test
    @DisplayName("로그인한 회원은 자기 장바구니 항목만 조회한다")
    void read_onlyMyItems() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(myItemId))
                .andExpect(jsonPath("$.data[0].auctionId").value(10));
    }

    @Test
    @DisplayName("토큰은 유효하지만 주문 회원이 없으면 404 USER_NOT_FOUND")
    void read_memberNotSynced() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(99L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMON103"));
    }

    @Test
    @DisplayName("결제 요청: 상품 목록이 비어 있으면 400")
    void payment_emptyProductIds() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));
    }

    @Test
    @DisplayName("결제 요청: 남의 상품이 섞여 있으면 403 CART_ITEM_FORBIDDEN(ORDER007), 주문 그룹과 주문을 만들지 않는다")
    void payment_withOthersItem() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20, 21]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER007"));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("결제 요청: 결제 기한이 지난 상품이 섞여 있으면 403 CART_ITEM_PAYMENT_EXPIRED(ORDER008), 주문 그룹과 주문을 만들지 않는다")
    void payment_expiredItemMixed() throws Exception {
        cartItemRepository.save(new CartItem(winner, seller, 12L, 22L, 40_000L, 2_500L, LocalDateTime.now().minusDays(2)));

        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20, 22]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER008"));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("결제 요청: 정상이면 200, 같은 요청 안에서 총액을 가진 주문 그룹 1개와 WAITING 주문을 만든다")
    void payment_success_createsGroupAndOrder() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20]}"))
                .andExpect(status().isOk());

        List<OrderGroup> groups = orderGroupRepository.findAll();
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getTotalPrice()).isEqualTo(33_000L);
        Long groupId = groups.get(0).getId();
        assertThat(orderRepository.findAll())
                .extracting(
                        order -> order.getGroup().getId(),
                        order -> order.getWinner().getId(),
                        order -> order.getSeller().getId(),
                        Order::getAuctionId,
                        Order::getProductId,
                        Order::getWinningPrice,
                        Order::getDeliveryFee,
                        Order::getState)
                .containsExactly(tuple(groupId, 1L, 2L, 10L, 20L, 30_000L, 3_000L, WAITING));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }
}
