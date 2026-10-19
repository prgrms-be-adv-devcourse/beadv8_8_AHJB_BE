package com.jjinmak.back.boundedContext.order.domain;

import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.DELIVERY_ERROR;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUNDED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_FORBIDDEN;
import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

class OrderTest {

    OrderMember winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
    OrderMember seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
    OrderMember other = new OrderMember(3L, UUID.randomUUID(), "다른 회원");

    OrderGroup group = new OrderGroup(33_000L);

    LocalDateTime shippedAt = LocalDateTime.of(2026, 10, 15, 12, 30);

    LocalDateTime paidAt = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Test
    @DisplayName("주문을 만들면 WAITING 상태이고, 모든 값이 제자리에 들어간다")
    void constructor_mapsAllFieldsAndStartsWaiting() {
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);

        assertThat(order.getState()).isEqualTo(WAITING);
        assertThat(order.getGroup()).isSameAs(group);
        assertThat(order.getWinner()).isSameAs(winner);
        assertThat(order.getSeller()).isSameAs(seller);
        assertThat(order.getAuctionId()).isEqualTo(10L);
        assertThat(order.getProductId()).isEqualTo(20L);
        assertThat(order.getWinningPrice()).isEqualTo(30_000L);
        assertThat(order.getDeliveryFee()).isEqualTo(3_000L);
        assertThat(order.getConfirmExpiredAt()).isNull();
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("현재 상태가 기대한 상태와 같으면 다음 상태로 바뀐다")
    void validateAndUpdateState_matchingState() {
        Order order = order(PAID);

        order.validateAndUpdateState(PAID, SHIPPING);

        assertThat(order.getState()).isEqualTo(SHIPPING);
    }

    @Test
    @DisplayName("현재 상태가 기대한 상태와 다르면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않는다")
    void validateAndUpdateState_otherState() {
        Order order = order(WAITING);

        assertThatThrownBy(() -> order.validateAndUpdateState(PAID, SHIPPING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(WAITING);
    }

    @Test
    @DisplayName("현재 상태가 기대한 상태와 같으면 상태 검증을 통과한다")
    void validateState_matchingState() {
        Order order = order(PAID);

        assertThatCode(() -> order.validateState(PAID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("현재 상태가 기대한 상태와 다르면 ORDER_STATE_BAD_REQUEST")
    void validateState_otherState() {
        Order order = order(PAID);

        assertThatThrownBy(() -> order.validateState(SHIPPING))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);
    }

    @Test
    @DisplayName("낙찰자는 낙찰자 검증을 통과한다")
    void validateWinner_winner() {
        Order order = order(WAITING);

        assertThatCode(() -> order.validateWinner(winner)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("낙찰자와 id가 같은 다른 회원 객체도 낙찰자 검증을 통과한다")
    void validateWinner_sameIdOtherInstance() {
        OrderMember savedWinner = new OrderMember(1_000L, UUID.randomUUID(), "낙찰자");
        Order order = new Order(group, savedWinner, seller, 10L, 20L, 30_000L, 3_000L);
        OrderMember sameWinner = new OrderMember(1_000L, UUID.randomUUID(), "낙찰자");

        assertThatCode(() -> order.validateWinner(sameWinner)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("판매자가 낙찰자 검증을 받으면 ORDER_FORBIDDEN")
    void validateWinner_seller() {
        Order order = order(WAITING);

        assertThatThrownBy(() -> order.validateWinner(seller))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_FORBIDDEN);
    }

    @Test
    @DisplayName("판매자는 판매자 검증을 통과한다")
    void validateSeller_seller() {
        Order order = order(WAITING);

        assertThatCode(() -> order.validateSeller(seller)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("판매자와 id가 같은 다른 회원 객체도 판매자 검증을 통과한다")
    void validateSeller_sameIdOtherInstance() {
        OrderMember savedSeller = new OrderMember(2_000L, UUID.randomUUID(), "판매자");
        Order order = new Order(group, winner, savedSeller, 10L, 20L, 30_000L, 3_000L);
        OrderMember sameSeller = new OrderMember(2_000L, UUID.randomUUID(), "판매자");

        assertThatCode(() -> order.validateSeller(sameSeller)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("낙찰자가 판매자 검증을 받으면 ORDER_FORBIDDEN")
    void validateSeller_winner() {
        Order order = order(WAITING);

        assertThatThrownBy(() -> order.validateSeller(winner))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_FORBIDDEN);
    }

    @Test
    @DisplayName("낙찰자는 주문 참여자 검증을 통과한다")
    void validateParticipant_winner() {
        Order order = order(WAITING);

        assertThatCode(() -> order.validateParticipant(winner)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("판매자는 주문 참여자 검증을 통과한다")
    void validateParticipant_seller() {
        Order order = order(WAITING);

        assertThatCode(() -> order.validateParticipant(seller)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("낙찰자와 id가 같은 다른 회원 객체도 주문 참여자 검증을 통과한다")
    void validateParticipant_sameIdAsWinner() {
        OrderMember savedWinner = new OrderMember(1_000L, UUID.randomUUID(), "낙찰자");
        Order order = new Order(group, savedWinner, seller, 10L, 20L, 30_000L, 3_000L);
        OrderMember sameWinner = new OrderMember(1_000L, UUID.randomUUID(), "낙찰자");

        assertThatCode(() -> order.validateParticipant(sameWinner)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("판매자와 id가 같은 다른 회원 객체도 주문 참여자 검증을 통과한다")
    void validateParticipant_sameIdAsSeller() {
        OrderMember savedSeller = new OrderMember(2_000L, UUID.randomUUID(), "판매자");
        Order order = new Order(group, winner, savedSeller, 10L, 20L, 30_000L, 3_000L);
        OrderMember sameSeller = new OrderMember(2_000L, UUID.randomUUID(), "판매자");

        assertThatCode(() -> order.validateParticipant(sameSeller)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("낙찰자도 판매자도 아닌 회원이 주문 참여자 검증을 받으면 ORDER_FORBIDDEN")
    void validateParticipant_otherMember() {
        Order order = order(WAITING);

        assertThatThrownBy(() -> order.validateParticipant(other))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_FORBIDDEN);
    }

    @Test
    @DisplayName("배송 중인 주문이 배송 완료되면 SHIPPED가 되고, 구매 확정 기한은 배송 완료 시각 + 7일이다")
    void shipped_fromShipping() {
        Order order = order(SHIPPING);

        order.shipped(shippedAt);

        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 12, 30));
    }

    @Test
    @DisplayName("배송 오류 상태의 주문이 배송 완료되면 SHIPPED가 되고, 구매 확정 기한은 배송 완료 시각 + 7일이다")
    void shipped_fromDeliveryError() {
        Order order = order(DELIVERY_ERROR);

        order.shipped(shippedAt);

        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 12, 30));
    }

    @ParameterizedTest(name = "{0} 주문이 배송 완료")
    @EnumSource(value = OrderState.class, names = {"SHIPPING", "DELIVERY_ERROR"}, mode = EXCLUDE)
    @DisplayName("배송 중도 배송 오류도 아닌 주문이 배송 완료되면 ORDER_STATE_BAD_REQUEST, 상태와 구매 확정 기한이 바뀌지 않는다")
    void shipped_notShippingOrDeliveryError(OrderState state) {
        Order order = order(state);

        assertThatThrownBy(() -> order.shipped(shippedAt))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(state);
        assertThat(order.getConfirmExpiredAt()).isNull();
    }

    @Test
    @DisplayName("결제 대기 주문이 결제되면 PAID가 되고, 결제 시각이 기록된다")
    void paid_fromWaiting() {
        Order order = order(WAITING);

        order.paid(paidAt);

        assertThat(order.getState()).isEqualTo(PAID);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("이미 결제된 주문이 다시 결제되면 ORDER_STATE_BAD_REQUEST, 상태와 처음 결제 시각이 그대로다")
    void paid_alreadyPaid() {
        Order order = order(WAITING);
        order.paid(paidAt);

        assertThatThrownBy(() -> order.paid(paidAt.plusHours(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(PAID);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("결제 대기도 결제 완료도 아닌 주문이 결제되면 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 결제 시각이 기록되지 않는다")
    void paid_notWaiting() {
        Order order = order(SHIPPING);

        assertThatThrownBy(() -> order.paid(paidAt))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

        assertThat(order.getState()).isEqualTo(SHIPPING);
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("상태를 직접 바꾸면 현재 상태와 관계없이 지정한 상태가 된다")
    void updateState_withoutValidation() {
        Order order = order(WAITING);

        order.updateState(REFUNDED);

        assertThat(order.getState()).isEqualTo(REFUNDED);
    }

    private Order order(OrderState state) {
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return order;
    }
}
