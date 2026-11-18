package com.jjinmak.back.boundedContext.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderGroupTest {

    OrderMember winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
    OrderMember seller = new OrderMember(2L, UUID.randomUUID(), "판매자");

    @Test
    @DisplayName("주문 그룹을 만들면 낙찰자와 총액을 담고, 주문 목록은 비어 있다")
    void constructor_keepsWinnerAndTotalPriceWithEmptyOrders() {
        OrderGroup group = new OrderGroup(winner, 87_000L);

        assertThat(group.getWinner()).isSameAs(winner);
        assertThat(group.getTotalPrice()).isEqualTo(87_000L);
        assertThat(group.getOrders()).isEmpty();
    }

    @Test
    @DisplayName("주문 그룹을 만들면 lifeTime은 만든 시각부터 1시간 뒤다")
    void constructor_setsLifeTimeOneHourLater() {
        LocalDateTime before = LocalDateTime.now();
        OrderGroup group = new OrderGroup(winner, 87_000L);
        LocalDateTime after = LocalDateTime.now();

        assertThat(group.getLifeTime()).isBetween(before.plusHours(1), after.plusHours(1));
    }

    @Test
    @DisplayName("lifeTime을 갱신하면 기존 lifeTime과 상관없이 갱신한 시각부터 1시간 뒤가 된다")
    void updateLifeTime_oneHourFromNow() {
        OrderGroup group = new OrderGroup(winner, 87_000L);
        ReflectionTestUtils.setField(group, "lifeTime", LocalDateTime.now().plusMinutes(1));

        LocalDateTime before = LocalDateTime.now();
        group.updateLifeTime();
        LocalDateTime after = LocalDateTime.now();

        assertThat(group.getLifeTime()).isBetween(before.plusHours(1), after.plusHours(1));
    }

    @Test
    @DisplayName("주문을 추가하면 추가한 순서대로 주문 목록에 들어간다")
    void add_appendsInOrder() {
        OrderGroup group = new OrderGroup(winner, 87_000L);
        Order first = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        Order second = new Order(group, winner, seller, 11L, 21L, 50_000L, 4_000L);

        group.add(first);
        group.add(second);

        assertThat(group.getOrders()).containsExactly(first, second);
    }
}
