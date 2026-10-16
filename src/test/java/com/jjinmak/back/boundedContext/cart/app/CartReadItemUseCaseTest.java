package com.jjinmak.back.boundedContext.cart.app;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import com.jjinmak.back.boundedContext.cart.out.CartItemRepository;
import com.jjinmak.back.boundedContext.cart.out.CartMemberRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.jjinmak.back.global.exception.CommonErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartReadItemUseCaseTest {

    @Mock
    CartMemberRepository cartMemberRepository;

    @Mock
    CartItemRepository cartItemRepository;

    @InjectMocks
    CartReadItemUseCase cartReadItemUseCase;

    CartMember winner;
    CartMember seller;

    @BeforeEach
    void setUp() {
        winner = new CartMember(1L, UUID.randomUUID(), "낙찰자");
        seller = new CartMember(2L, UUID.randomUUID(), "판매자");
    }

    @Test
    @DisplayName("낙찰자의 장바구니 항목을 저장소가 준 순서 그대로 DTO로 돌려준다")
    void readWinnerItems_keepsRepositoryOrder() {
        CartItem first = cartItem(11L, LocalDateTime.of(2026, 10, 15, 9, 0));
        CartItem second = cartItem(10L, LocalDateTime.of(2026, 10, 15, 10, 0));

        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartItemRepository.findAllByWinnerOrderByWinAt(winner)).thenReturn(List.of(first, second));

        List<CartItemDto> result = cartReadItemUseCase.readWinnerItems(1L);

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
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.of(winner));
        when(cartItemRepository.findAllByWinnerOrderByWinAt(winner)).thenReturn(List.of());

        List<CartItemDto> result = cartReadItemUseCase.readWinnerItems(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("장바구니 회원이 없으면 USER_NOT_FOUND이고, 항목은 조회하지 않는다")
    void readWinnerItems_memberNotFound() {
        when(cartMemberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartReadItemUseCase.readWinnerItems(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        verifyNoInteractions(cartItemRepository);
    }

    private CartItem cartItem(Long auctionId, LocalDateTime winAt) {
        return new CartItem(winner, seller, auctionId, auctionId + 100, 30_000L, 3_000L, winAt);
    }
}
