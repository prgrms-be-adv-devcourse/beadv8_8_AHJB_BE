package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCreateCartItemUseCaseTest {

    @Mock
    CartItemRepository cartItemRepository;

    @Mock
    CartMemberRepository cartMemberRepository;

    @InjectMocks
    CartCreateCartItemUseCase cartCreateCartItemUseCase;

    CartMember winner;
    CartMember seller;

    LocalDateTime winAt = LocalDateTime.of(2026, 10, 15, 12, 0);

    AuctionWonEvent event = new AuctionWonEvent(10L, 20L, 1L, 2L, 30_000L, 3_000L, winAt);

    @BeforeEach
    void setUp() {
        winner = new CartMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new CartMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("낙찰 이벤트의 값이 장바구니 항목의 제자리에 저장된다")
    void createCartItem_mapsEventToItem() {
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartMemberRepository.findById(2L)).thenReturn(Optional.of(seller));

        cartCreateCartItemUseCase.createCartItem(event);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());

        CartItem saved = captor.getValue();
        assertThat(saved.getWinner()).isSameAs(winner);
        assertThat(saved.getSeller()).isSameAs(seller);
        assertThat(saved.getAuctionId()).isEqualTo(10L);
        assertThat(saved.getProductId()).isEqualTo(20L);
        assertThat(saved.getWinningPrice()).isEqualTo(30_000L);
        assertThat(saved.getDeliveryFee()).isEqualTo(3_000L);
        assertThat(saved.getWinAt()).isEqualTo(winAt);
        assertThat(saved.getPaymentDueAt()).isEqualTo(winAt.plusDays(1));
    }

    @Test
    @DisplayName("낙찰자가 장바구니 회원으로 동기화되지 않았으면 USER_NOT_FOUND, 저장하지 않는다")
    void createCartItem_winnerNotSynced() {
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartCreateCartItemUseCase.createCartItem(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    @DisplayName("판매자가 장바구니 회원으로 동기화되지 않았으면 USER_NOT_FOUND, 저장하지 않는다")
    void createCartItem_sellerNotSynced() {
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartMemberRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartCreateCartItemUseCase.createCartItem(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository);
    }
}
