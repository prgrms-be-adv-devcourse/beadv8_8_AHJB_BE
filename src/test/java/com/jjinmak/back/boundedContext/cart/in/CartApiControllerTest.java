package com.jjinmak.back.boundedContext.cart.in;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.security.JwtTokenProvider;
import com.jjinmak.back.shared.cart.event.CartCreateOrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class CartApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    CartMemberRepository cartMemberRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    ApplicationEvents events;

    Long myItemId;

    @BeforeEach
    void setUp() {
        CartMember winner = cartMemberRepository.save(new CartMember(1L, UUID.randomUUID(), "낙찰자"));
        CartMember seller = cartMemberRepository.save(new CartMember(2L, UUID.randomUUID(), "판매자"));
        CartMember other = cartMemberRepository.save(new CartMember(3L, UUID.randomUUID(), "다른 낙찰자"));

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
    @DisplayName("토큰은 유효하지만 장바구니 회원이 없으면 404 USER_NOT_FOUND")
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
    @DisplayName("결제 요청: 남의 상품이 섞여 있으면 403 CART_ITEM_FORBIDDEN, 주문 생성 이벤트를 발행하지 않는다")
    void payment_withOthersItem() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20, 21]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CART002"));

        assertThat(events.stream(CartCreateOrderEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 요청: 정상이면 200, 주문 생성 이벤트를 한 번 발행한다")
    void payment_success() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20]}"))
                .andExpect(status().isOk());

        List<CartCreateOrderEvent> published = events.stream(CartCreateOrderEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).totalPrice()).isEqualTo(33_000L);
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }
}
