package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.cart.event.CartCreateOrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.cart.exception.CartErrorCode.CART_ITEM_FORBIDDEN;
import static com.jjinmak.back.boundedContext.cart.exception.CartErrorCode.CART_ITEM_PAYMENT_EXPIRED;
import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartPaymentUseCaseTest {

    @Mock
    CartMemberRepository cartMemberRepository;

    @Mock
    CartItemRepository cartItemRepository;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    CartPaymentUseCase cartPaymentUseCase;

    CartMember winner;
    CartMember seller;

    @BeforeEach
    void setUp() {
        winner = new CartMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new CartMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("결제 요청에 성공하면 총액과 항목을 담은 주문 생성 이벤트를 발행한다")
    void payment_publishesCreateOrderEvent() {
        CartItem item1 = cartItem(10L, 20L, 30_000L, 3_000L, LocalDateTime.now());
        CartItem item2 = cartItem(11L, 21L, 50_000L, 4_000L, LocalDateTime.now());

        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 21L)))
                .thenReturn(List.of(item1, item2));

        cartPaymentUseCase.payment(1L, List.of(20L, 21L));

        ArgumentCaptor<CartCreateOrderEvent> captor = ArgumentCaptor.forClass(CartCreateOrderEvent.class);
        verify(eventPublisher).publish(captor.capture());

        CartCreateOrderEvent event = captor.getValue();
        assertThat(event.totalPrice()).isEqualTo(87_000L);
        assertThat(event.cartItems())
                .extracting(CartItemDto::auctionId)
                .containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("같은 상품 id를 중복으로 요청해도 한 번만 결제 대상에 넣는다")
    void payment_duplicateProductIds() {
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));

        cartPaymentUseCase.payment(1L, List.of(20L, 20L));

        ArgumentCaptor<CartCreateOrderEvent> captor = ArgumentCaptor.forClass(CartCreateOrderEvent.class);
        verify(eventPublisher).publish(captor.capture());

        assertThat(captor.getValue().cartItems()).hasSize(1);
        assertThat(captor.getValue().totalPrice()).isEqualTo(33_000L);
    }

    @Test
    @DisplayName("요청한 상품 중 내 장바구니에 없는 것이 있으면 CART_ITEM_FORBIDDEN, 이벤트를 발행하지 않는다")
    void payment_itemNotInMyCart() {
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L, 99L)))
                .thenReturn(List.of(cartItem(10L, 20L, 30_000L, 3_000L, LocalDateTime.now())));

        assertThatThrownBy(() -> cartPaymentUseCase.payment(1L, List.of(20L, 99L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_FORBIDDEN);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제 기한이 지난 항목이 있으면 CART_ITEM_PAYMENT_EXPIRED, 이벤트를 발행하지 않는다")
    void payment_expired() {
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(10L, 20L, 30_000L, 3_000L, twoDaysAgo)));

        assertThatThrownBy(() -> cartPaymentUseCase.payment(1L, List.of(20L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_PAYMENT_EXPIRED);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결제 기한 직전이면 결제할 수 있다")
    void payment_justBeforeDue() {
        LocalDateTime winAt = LocalDateTime.now().minusDays(1).plusMinutes(1);
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartItemRepository.findAllByWinnerAndProductIdIn(winner, Set.of(20L)))
                .thenReturn(List.of(cartItem(10L, 20L, 30_000L, 3_000L, winAt)));

        cartPaymentUseCase.payment(1L, List.of(20L));

        verify(eventPublisher).publish(any(CartCreateOrderEvent.class));
    }

    @Test
    @DisplayName("장바구니 회원이 없으면 USER_NOT_FOUND, 항목 조회와 이벤트 발행을 하지 않는다")
    void payment_memberNotFound() {
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartPaymentUseCase.payment(1L, List.of(20L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository, eventPublisher);
    }

    private CartItem cartItem(Long auctionId, Long productId, Long winningPrice, Long deliveryFee, LocalDateTime winAt) {
        return new CartItem(winner, seller, auctionId, productId, winningPrice, deliveryFee, winAt);
    }
}
