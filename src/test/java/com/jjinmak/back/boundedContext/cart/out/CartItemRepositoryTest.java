package com.jjinmak.back.boundedContext.cart.out;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CartItemRepositoryTest {

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    CartMemberRepository cartMemberRepository;

    @Autowired
    TestEntityManager em;

    CartMember a;
    CartMember b;
    CartMember c;

    @BeforeEach
    void setUp() {
        a = cartMemberRepository.save(new CartMember(1L, UUID.randomUUID(), "회원A"));
        b = cartMemberRepository.save(new CartMember(2L, UUID.randomUUID(), "회원B"));
        c = cartMemberRepository.save(new CartMember(3L, UUID.randomUUID(), "회원C"));
    }

    @Test
    @DisplayName("낙찰자 기준 목록: 내가 낙찰한 항목만, 낙찰 시각 오름차순으로 나온다")
    void findAllByWinnerOrderByWinAt() {
        save(a, b, 10L, 20L, at(10, 0));
        save(a, b, 11L, 21L, at(9, 0));
        save(b, a, 12L, 22L, at(8, 0));
        save(c, b, 13L, 23L, at(7, 0));
        em.flush();
        em.clear();

        List<CartItem> items = cartItemRepository.findAllByWinnerOrderByWinAt(a);

        assertThat(items).extracting(CartItem::getAuctionId).containsExactly(11L, 10L);
        assertThat(items).allSatisfy(item -> assertThat(Hibernate.isInitialized(item.getSeller())).isTrue());
    }

    @Test
    @DisplayName("결제 대상 조회: 같은 상품 id라도 내 항목만 나온다")
    void findAllByWinnerAndProductIdIn() {
        save(a, b, 10L, 20L, at(10, 0));
        save(c, b, 11L, 20L, at(10, 0));
        save(a, b, 12L, 21L, at(10, 0));
        em.flush();
        em.clear();

        List<CartItem> items = cartItemRepository.findAllByWinnerAndProductIdIn(a, Set.of(20L));

        assertThat(items).extracting(CartItem::getAuctionId).containsExactly(10L);
        assertThat(items).allSatisfy(item -> assertThat(Hibernate.isInitialized(item.getSeller())).isTrue());
    }

    @Test
    @DisplayName("경매 id로 항목을 찾는다")
    void findByAuctionId() {
        save(a, b, 10L, 20L, at(10, 0));
        save(a, b, 11L, 21L, at(10, 0));
        em.flush();
        em.clear();

        Optional<CartItem> item = cartItemRepository.findByAuctionId(11L);

        assertThat(item).isPresent();
        assertThat(item.get().getProductId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("만료 항목: 결제 기한이 기준 시각보다 이전인 것만 나온다 (같은 시각은 제외)")
    void findExpiredItemIds() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 16, 12, 0);
        CartItem expired = save(a, b, 10L, 20L, base.minusDays(1).minusMinutes(1));
        save(a, b, 11L, 21L, base.minusDays(1));
        save(a, b, 12L, 22L, base.minusDays(1).plusMinutes(1));
        em.flush();
        em.clear();

        List<Long> ids = cartItemRepository.findExpiredItemIds(base);

        assertThat(ids).containsExactly(expired.getId());
    }

    @Test
    @DisplayName("참여자와 함께 조회하면 낙찰자·판매자가 이미 로딩되어 있다")
    void findByIdWithParticipant() {
        CartItem saved = save(a, b, 10L, 20L, at(10, 0));
        em.flush();
        em.clear();

        CartItem item = cartItemRepository.findByIdWithParticipant(saved.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(item.getWinner())).isTrue();
        assertThat(Hibernate.isInitialized(item.getSeller())).isTrue();
    }

    @Test
    @DisplayName("같은 경매 id로 두 번 저장할 수 없다")
    void auctionId_isUnique() {
        save(a, b, 10L, 20L, at(10, 0));
        em.flush();

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(
                new CartItem(c, b, 10L, 21L, 30_000L, 3_000L, at(11, 0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CartItem save(CartMember winner, CartMember seller, Long auctionId, Long productId, LocalDateTime winAt) {
        return cartItemRepository.save(new CartItem(winner, seller, auctionId, productId, 30_000L, 3_000L, winAt));
    }

    private LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 15, hour, minute);
    }
}
