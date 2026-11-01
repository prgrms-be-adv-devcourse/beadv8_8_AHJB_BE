package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUNDED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    TestEntityManager em;

    OrderMember a;
    OrderMember b;
    OrderMember c;

    OrderGroup group;

    @BeforeEach
    void setUp() {
        a = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "회원A"));
        b = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "회원B"));
        c = orderMemberRepository.save(new OrderMember(3L, UUID.randomUUID(), "회원C"));
        group = orderGroupRepository.save(new OrderGroup(a, 33_000L));
    }

    @Test
    @DisplayName("낙찰자 기준 목록: 내가 낙찰한 주문 중 요청한 상태만, 생성 시각 내림차순으로 나오고 전체 개수는 조건에 맞는 주문 수다")
    void findAllByWinnerAndStateIn() {
        save(a, b, 10L, PAID, at(9, 0));
        save(a, b, 11L, SHIPPING, at(11, 0));
        save(a, c, 12L, CONFIRMED, at(10, 0));
        save(a, b, 13L, WAITING, at(12, 0));
        save(b, a, 14L, PAID, at(13, 0));
        save(c, b, 15L, PAID, at(14, 0));
        em.flush();
        em.clear();

        Page<Order> page = orderRepository.findAllByWinnerAndStateIn(a, Set.of(PAID, SHIPPING, CONFIRMED), PageRequest.of(0, 2));

        assertThat(page.getContent()).extracting(Order::getAuctionId).containsExactly(11L, 12L);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).allSatisfy(order -> assertThat(Hibernate.isInitialized(order.getSeller())).isTrue());
    }

    @Test
    @DisplayName("판매자 기준 목록: 내가 판매한 주문 중 요청한 상태만, 생성 시각 내림차순으로 나오고 전체 개수는 조건에 맞는 주문 수다")
    void findAllBySellerAndStateIn() {
        save(a, b, 10L, PAID, at(9, 0));
        save(c, b, 11L, SHIPPED, at(11, 0));
        save(a, b, 12L, WAITING, at(12, 0));
        save(b, a, 13L, PAID, at(13, 0));
        save(a, c, 14L, PAID, at(14, 0));
        em.flush();
        em.clear();

        Page<Order> page = orderRepository.findAllBySellerAndStateIn(b, Set.of(PAID, SHIPPED), PageRequest.of(0, 1));

        assertThat(page.getContent()).extracting(Order::getAuctionId).containsExactly(11L);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(order -> assertThat(Hibernate.isInitialized(order.getWinner())).isTrue());
    }

    @Test
    @DisplayName("상태 조건 조회: 요청한 상태이면 조회되고, 낙찰자·판매자가 이미 로딩되어 있다")
    void findByIdAndStateInWithWinnerAndSeller() {
        Order saved = save(a, b, 10L, SHIPPED);
        em.flush();
        em.clear();

        Order order = orderRepository.findByIdAndStateInWithWinnerAndSeller(saved.getId(), Set.of(SHIPPED, CONFIRMED)).orElseThrow();

        assertThat(order.getWinner().getId()).isEqualTo(a.getId());
        assertThat(order.getSeller().getId()).isEqualTo(b.getId());
        assertThat(Hibernate.isInitialized(order.getWinner())).isTrue();
        assertThat(Hibernate.isInitialized(order.getSeller())).isTrue();
    }

    @Test
    @DisplayName("상태 조건 조회: 요청한 상태가 아니면 조회되지 않는다")
    void findByIdAndStateInWithWinnerAndSeller_stateNotIn() {
        Order saved = save(a, b, 10L, WAITING);
        em.flush();
        em.clear();

        Optional<Order> order = orderRepository.findByIdAndStateInWithWinnerAndSeller(saved.getId(), Set.of(PAID, SHIPPED));

        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("참여자와 함께 조회하면 상태와 관계없이 조회되고, 낙찰자·판매자가 이미 로딩되어 있다")
    void findByIdWithWinnerAndSeller() {
        Order saved = save(a, b, 10L, REFUNDED);
        em.flush();
        em.clear();

        Order order = orderRepository.findByIdWithWinnerAndSeller(saved.getId()).orElseThrow();

        assertThat(order.getAuctionId()).isEqualTo(10L);
        assertThat(order.getWinner().getId()).isEqualTo(a.getId());
        assertThat(order.getSeller().getId()).isEqualTo(b.getId());
        assertThat(Hibernate.isInitialized(order.getWinner())).isTrue();
        assertThat(Hibernate.isInitialized(order.getSeller())).isTrue();
    }

    @Test
    @DisplayName("참여자와 함께 조회: 없는 주문 id면 비어 있다")
    void findByIdWithWinnerAndSeller_unknownId() {
        save(a, b, 10L, PAID);
        em.flush();
        em.clear();

        Optional<Order> order = orderRepository.findByIdWithWinnerAndSeller(-1L);

        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("구매 확정 기한 만료: 배송 완료(SHIPPED) 주문 중 기한이 기준 시각보다 이전인 것만 나온다 (같은 시각, 다른 상태는 제외)")
    void findExpiredOrderIds() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 16, 12, 0);
        Order expired = shipped(10L, base.minusDays(7).minusMinutes(1));
        shipped(11L, base.minusDays(7));
        shipped(12L, base.minusDays(7).plusMinutes(1));
        Order refundRequested = shipped(13L, base.minusDays(8));
        refundRequested.updateState(REFUND_REQUESTED);
        em.flush();
        em.clear();

        List<Long> ids = orderRepository.findExpiredOrderIds(base);

        assertThat(ids).containsExactly(expired.getId());
    }

    @Test
    @DisplayName("결제 대기 초과: 결제 대기(WAITING) 주문 중 주문 그룹의 lifeTime이 기준 시각보다 이전인 것만 나온다 (같은 시각은 제외, lifeTime이 남은 그룹은 주문이 오래전에 생성됐어도 제외, 다른 상태는 제외)")
    void findOverWaitedAll() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 16, 12, 0);
        save(groupWithLifeTime(base.minusMinutes(1)), 10L, WAITING);
        save(groupWithLifeTime(base), 11L, WAITING);
        Order createdLongAgo = save(groupWithLifeTime(base.plusMinutes(1)), 12L, WAITING);
        save(groupWithLifeTime(base.minusDays(1)), 13L, PAID);
        setCreatedAt(createdLongAgo, base.minusDays(1));
        em.clear();

        List<Order> orders = orderRepository.findOverWaitedAll(base);

        assertThat(orders).extracting(Order::getAuctionId).containsExactly(10L);
    }

    @Test
    @DisplayName("미발송 주문: 결제 완료(PAID) 주문 중 결제 시각이 기준 시각보다 이전인 것만 나온다 (같은 시각, 발송을 시작했거나 환불을 요청한 주문은 제외)")
    void findNonShippedOrderIds() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 16, 12, 0);
        Order nonShipped = paid(10L, base.minusMinutes(1));
        paid(11L, base);
        paid(12L, base.plusMinutes(1));
        Order shipping = paid(13L, base.minusDays(1));
        shipping.updateState(SHIPPING);
        Order refundRequested = paid(14L, base.minusDays(1));
        refundRequested.updateState(REFUND_REQUESTED);
        em.flush();
        em.clear();

        List<Long> ids = orderRepository.findNonShippedOrderIds(base);

        assertThat(ids).containsExactly(nonShipped.getId());
    }

    private Order save(OrderMember winner, OrderMember seller, Long auctionId, OrderState state) {
        Order order = new Order(group, winner, seller, auctionId, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return orderRepository.save(order);
    }

    private Order save(OrderMember winner, OrderMember seller, Long auctionId, OrderState state, LocalDateTime createdAt) {
        Order order = save(winner, seller, auctionId, state);
        setCreatedAt(order, createdAt);
        return order;
    }

    private Order save(OrderGroup group, Long auctionId, OrderState state) {
        Order order = new Order(group, a, b, auctionId, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return orderRepository.save(order);
    }

    private void setCreatedAt(Order order, LocalDateTime createdAt) {
        em.flush();
        em.getEntityManager()
                .createQuery("update Order o set o.createdAt = :createdAt where o.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", order.getId())
                .executeUpdate();
    }

    private OrderGroup groupWithLifeTime(LocalDateTime lifeTime) {
        OrderGroup group = new OrderGroup(a, 33_000L);
        ReflectionTestUtils.setField(group, "lifeTime", lifeTime);
        return orderGroupRepository.save(group);
    }

    private Order paid(Long auctionId, LocalDateTime paidAt) {
        Order order = new Order(group, a, b, auctionId, 20L, 30_000L, 3_000L);
        order.paid(paidAt);
        return orderRepository.save(order);
    }

    private Order shipped(Long auctionId, LocalDateTime shippedAt) {
        Order order = new Order(group, a, b, auctionId, 20L, 30_000L, 3_000L);
        order.updateState(SHIPPING);
        order.shipped(shippedAt);
        return orderRepository.save(order);
    }

    private LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 15, hour, minute);
    }
}
