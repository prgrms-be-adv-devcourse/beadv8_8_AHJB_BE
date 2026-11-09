package com.jjinmak.order.domain;

import com.jjinmak.contracts.cart.dto.CartItemDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemTest {

    OrderMember winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
    OrderMember seller = new OrderMember(2L, UUID.randomUUID(), "판매자");

    LocalDateTime winAt = LocalDateTime.of(2026, 10, 15, 12, 30);

    @Test
    @DisplayName("결제 기한은 낙찰 시각 + 1일이다")
    void paymentDueAt_isOneDayAfterWin() {
        CartItem item = new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, winAt);

        assertThat(item.getPaymentDueAt()).isEqualTo(LocalDateTime.of(2026, 10, 16, 12, 30));
    }

    @Test
    @DisplayName("DTO로 바꾸면 모든 값이 제자리에 들어간다")
    void dto_mapsAllFields() {
        CartItem item = new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, winAt);

        CartItemDto dto = item.dto();

        assertThat(dto).isEqualTo(new CartItemDto(
                null,
                winner.getUuid(), seller.getUuid(),
                10L, 20L, 30_000L, 3_000L,
                winAt, winAt.plusDays(1)
        ));
    }

    @Test
    @DisplayName("새 항목은 결제 임박 알림을 보내지 않은 상태다")
    void paymentDefaultAlarmed_falseOnCreate() {
        CartItem item = new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, winAt);

        assertThat(item.getPaymentDefaultAlarmed()).isFalse();
    }

    @Test
    @DisplayName("결제 임박 알림을 보내면 알림을 보낸 상태가 되고, 결제 기한은 그대로다")
    void sendPaymentReminder_marksAlarmed() {
        CartItem item = new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, winAt);

        item.sendPaymentReminder();

        assertThat(item.getPaymentDefaultAlarmed()).isTrue();
        assertThat(item.getPaymentDueAt()).isEqualTo(LocalDateTime.of(2026, 10, 16, 12, 30));
    }
}
