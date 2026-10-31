package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderGroupRepositoryTest {

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    TestEntityManager em;

    OrderMember a;
    OrderMember b;
    OrderMember c;

    @BeforeEach
    void setUp() {
        a = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "회원A"));
        b = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "회원B"));
        c = orderMemberRepository.save(new OrderMember(3L, UUID.randomUUID(), "회원C"));
    }

    @Test
    @DisplayName("주문과 함께 조회하면 그 그룹의 주문만 이미 로딩되어 있다")
    void findByIdWithOrders() {
        OrderGroup group = orderGroupRepository.save(new OrderGroup(a, 66_000L));
        OrderGroup other = orderGroupRepository.save(new OrderGroup(c, 33_000L));
        save(group, a, 10L);
        save(group, a, 11L);
        save(other, c, 12L);
        em.flush();
        em.clear();

        OrderGroup found = orderGroupRepository.findByIdWithOrders(group.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(found.getOrders())).isTrue();
        assertThat(found.getOrders()).extracting(Order::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("주문과 함께 조회: 주문이 하나도 없는 그룹은 조회되지 않는다 (inner join)")
    void findByIdWithOrders_noOrders() {
        OrderGroup group = orderGroupRepository.save(new OrderGroup(a, 0L));
        em.flush();
        em.clear();

        Optional<OrderGroup> found = orderGroupRepository.findByIdWithOrders(group.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("주문에서 꺼낸 그룹 프록시로 그룹을 지우면 그 그룹의 주문도 함께 지워지고, 다른 그룹은 남는다")
    void deleteAll_groupsFromOrders() {
        OrderGroup first = orderGroupRepository.save(new OrderGroup(a, 66_000L));
        OrderGroup second = orderGroupRepository.save(new OrderGroup(c, 33_000L));
        OrderGroup kept = orderGroupRepository.save(new OrderGroup(c, 33_000L));
        Order first1 = save(first, a, 10L);
        Order first2 = save(first, a, 11L);
        Order second1 = save(second, c, 12L);
        Order kept1 = save(kept, c, 13L);
        em.flush();
        em.clear();
        OrderGroup firstProxy = orderRepository.findById(first1.getId()).orElseThrow().getGroup();
        OrderGroup secondProxy = orderRepository.findById(second1.getId()).orElseThrow().getGroup();

        orderGroupRepository.deleteAll(Set.of(firstProxy, secondProxy));
        em.flush();
        em.clear();

        assertThat(orderGroupRepository.findAllById(List.of(first.getId(), second.getId(), kept.getId())))
                .extracting(OrderGroup::getId).containsExactly(kept.getId());
        assertThat(orderRepository.findAllById(List.of(first1.getId(), first2.getId(), second1.getId(), kept1.getId())))
                .extracting(Order::getAuctionId).containsExactly(13L);
    }

    @Test
    @DisplayName("재사용 후보 조회: 그 낙찰자의 그룹 중 lifeTime이 기준 시각보다 뒤인 그룹만 주문과 함께 한 번씩 나온다 (같은 시각, 이미 지난 그룹, 다른 낙찰자의 그룹은 제외)")
    void findAllByWinnerAndLifeTime() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 16, 12, 0);
        OrderGroup alive = groupWithLifeTime(a, base.plusMinutes(1));
        OrderGroup sameTime = groupWithLifeTime(a, base);
        OrderGroup expired = groupWithLifeTime(a, base.minusMinutes(1));
        OrderGroup othersAlive = groupWithLifeTime(c, base.plusMinutes(1));
        save(alive, a, 10L);
        save(alive, a, 11L);
        save(sameTime, a, 12L);
        save(expired, a, 13L);
        save(othersAlive, c, 14L);
        em.flush();
        em.clear();

        List<OrderGroup> found = orderGroupRepository.findAllByWinnerAndLifeTime(a, base);

        assertThat(found).extracting(OrderGroup::getId).containsExactly(alive.getId());
        assertThat(Hibernate.isInitialized(found.getFirst().getOrders())).isTrue();
        assertThat(found.getFirst().getOrders()).extracting(Order::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
    }

    private OrderGroup groupWithLifeTime(OrderMember winner, LocalDateTime lifeTime) {
        OrderGroup group = new OrderGroup(winner, 33_000L);
        ReflectionTestUtils.setField(group, "lifeTime", lifeTime);
        return orderGroupRepository.save(group);
    }

    private Order save(OrderGroup group, OrderMember winner, Long auctionId) {
        return orderRepository.save(new Order(group, winner, b, auctionId, 20L, 30_000L, 3_000L));
    }
}
