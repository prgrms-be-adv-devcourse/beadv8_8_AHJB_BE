package com.jjinmak.back.boundedContext.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderGroupTest {

    OrderMember winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
    OrderMember seller = new OrderMember(2L, UUID.randomUUID(), "판매자");

    @Test
    @DisplayName("주문 그룹을 만들면 총액을 담고, 주문 목록은 비어 있다")
    void constructor_keepsTotalPriceWithEmptyOrders() {
        OrderGroup group = new OrderGroup(87_000L);

        assertThat(group.getTotalPrice()).isEqualTo(87_000L);
        assertThat(group.getOrders()).isEmpty();
    }

    @Test
    @DisplayName("주문을 추가하면 추가한 순서대로 주문 목록에 들어간다")
    void add_appendsInOrder() {
        OrderGroup group = new OrderGroup(87_000L);
        Order first = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        Order second = new Order(group, winner, seller, 11L, 21L, 50_000L, 4_000L);

        group.add(first);
        group.add(second);

        assertThat(group.getOrders()).containsExactly(first, second);
    }
}
