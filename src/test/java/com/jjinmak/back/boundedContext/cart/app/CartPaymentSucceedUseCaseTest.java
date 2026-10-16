package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.cart.exception.CartErrorCode.CART_ITEM_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartPaymentSucceedUseCaseTest {

    @Mock
    CartItemRepository cartItemRepository;

    @InjectMocks
    CartPaymentSucceedUseCase cartPaymentSucceedUseCase;

    @Test
    @DisplayName("결제에 성공한 경매의 장바구니 항목을 삭제한다")
    void paymentSucceed_deletesItem() {
        CartItem item = cartItem(10L);
        when(cartItemRepository.findByAuctionId(10L)).thenReturn(Optional.of(item));

        cartPaymentSucceedUseCase.paymentSucceed(10L);

        verify(cartItemRepository).delete(item);
    }

    @Test
    @DisplayName("장바구니에 없는 경매면 CART_ITEM_NOT_FOUND, 아무것도 삭제하지 않는다")
    void paymentSucceed_notFound() {
        when(cartItemRepository.findByAuctionId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartPaymentSucceedUseCase.paymentSucceed(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CART_ITEM_NOT_FOUND);

        verify(cartItemRepository, never()).delete(any(CartItem.class));
    }

    private CartItem cartItem(Long auctionId) {
        CartMember winner = new CartMember(1L, UUID.randomUUID(), "낙찰자");
        CartMember seller = new CartMember(2L, UUID.randomUUID(), "판매자");
        return new CartItem(winner, seller, auctionId, 20L, 30_000L, 3_000L, LocalDateTime.now());
    }
}
