package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.event.CartPaymentDefaultEvent;
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
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.exception.OrderErrorCode.CART_ITEM_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartDefaultPaymentUseCaseTest {

    @Mock
    EventPublisher eventPublisher;

    @Mock
    CartItemRepository cartItemRepository;

    @InjectMocks
    CartDefaultPaymentUseCase cartDefaultPaymentUseCase;

    @Test
    @DisplayName("결제 기한이 1시간 넘게 지난 항목을 찾는다")
    void findExpiredItemIds_usesOneHourAgo() {
        when(cartItemRepository.findExpiredItemIds(any(LocalDateTime.class))).thenReturn(List.of(5L, 6L));

        LocalDateTime before = LocalDateTime.now().minusHours(1);
        List<Long> ids = cartDefaultPaymentUseCase.findExpiredItemIds();
        LocalDateTime after = LocalDateTime.now().minusHours(1);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cartItemRepository).findExpiredItemIds(captor.capture());

        assertThat(captor.getValue()).isBetween(before, after);
        assertThat(ids).containsExactly(5L, 6L);
    }

    @Test
    @DisplayName("만료 항목을 삭제하고, 미납 이벤트를 발행한다")
    void deleteExpiredItem_deletesAndPublishes() {
        OrderMember winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        OrderMember seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
        CartItem item = new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.of(2026, 10, 13, 12, 0));
        when(cartItemRepository.findByIdWithParticipant(7L)).thenReturn(Optional.of(item));

        cartDefaultPaymentUseCase.deleteExpiredItem(7L);

        verify(cartItemRepository).delete(item);
        verify(eventPublisher).publish(new CartPaymentDefaultEvent(10L, 20L, 1L, 2L, 30_000L, 3_000L));
    }

    @Test
    @DisplayName("없는 항목이면 CART_ITEM_NOT_FOUND, 삭제와 이벤트 발행을 하지 않는다")
    void deleteExpiredItem_notFound() {
        when(cartItemRepository.findByIdWithParticipant(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartDefaultPaymentUseCase.deleteExpiredItem(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_NOT_FOUND);

        verify(cartItemRepository, never()).delete(any(CartItem.class));
        verifyNoInteractions(eventPublisher);
    }
}
