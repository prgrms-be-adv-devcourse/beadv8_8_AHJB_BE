package com.jjinmak.back.boundedContext.order.app;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
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
import java.util.UUID;

import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartItemUseCaseTest {

    @Mock
    OrderSupport orderSupport;

    @Mock
    CartItemRepository cartItemRepository;

    @InjectMocks
    CartItemUseCase cartItemUseCase;

    OrderMember winner;
    OrderMember seller;

    LocalDateTime winAt = LocalDateTime.of(2026, 10, 15, 12, 0);

    AuctionWonEvent event = new AuctionWonEvent(10L, 20L, 1L, 2L, 30_000L, 3_000L, winAt);

    @BeforeEach
    void setUp() {
        winner = new OrderMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new OrderMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("낙찰 이벤트의 값이 장바구니 항목의 제자리에 저장된다")
    void createCartItem_mapsEventToItem() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getMember(2L)).thenReturn(seller);

        cartItemUseCase.createCartItem(event);

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
    @DisplayName("낙찰자가 주문 회원으로 동기화되지 않았으면 USER_NOT_FOUND, 저장하지 않는다")
    void createCartItem_winnerNotSynced() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> cartItemUseCase.createCartItem(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    @DisplayName("판매자가 주문 회원으로 동기화되지 않았으면 USER_NOT_FOUND, 저장하지 않는다")
    void createCartItem_sellerNotSynced() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(orderSupport.getMember(2L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> cartItemUseCase.createCartItem(event))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository);
    }

    @Test
    @DisplayName("낙찰자의 장바구니 항목을 저장소가 준 순서 그대로 DTO로 돌려준다")
    void readWinnerItems_keepsRepositoryOrder() {
        CartItem first = cartItem(11L, LocalDateTime.of(2026, 10, 15, 9, 0));
        CartItem second = cartItem(10L, LocalDateTime.of(2026, 10, 15, 10, 0));

        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerOrderByWinAt(winner)).thenReturn(List.of(first, second));

        List<CartItemDto> result = cartItemUseCase.readWinnerItems(1L);

        assertThat(result)
                .extracting(CartItemDto::auctionId)
                .containsExactly(11L, 10L);
        assertThat(result).allSatisfy(dto -> {
            assertThat(dto.winnerId()).isEqualTo(winner.getUuid());
            assertThat(dto.sellerId()).isEqualTo(seller.getUuid());
        });
    }

    @Test
    @DisplayName("장바구니가 비어 있으면 빈 목록을 돌려준다")
    void readWinnerItems_empty() {
        when(orderSupport.getMember(1L)).thenReturn(winner);
        when(cartItemRepository.findAllByWinnerOrderByWinAt(winner)).thenReturn(List.of());

        List<CartItemDto> result = cartItemUseCase.readWinnerItems(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("주문 회원이 없으면 USER_NOT_FOUND이고, 항목은 조회하지 않는다")
    void readWinnerItems_memberNotFound() {
        when(orderSupport.getMember(1L)).thenThrow(new BusinessException(USER_NOT_FOUND));

        assertThatThrownBy(() -> cartItemUseCase.readWinnerItems(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository);
    }

    private CartItem cartItem(Long auctionId, LocalDateTime winAt) {
        return new CartItem(winner, seller, auctionId, auctionId + 100, 30_000L, 3_000L, winAt);
    }
}
