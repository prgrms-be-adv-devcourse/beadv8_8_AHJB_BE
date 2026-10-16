package com.jjinmak.back.boundedContext.cart.in;

import com.jjinmak.back.boundedContext.cart.app.CartFacade;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.jjinmak.back.boundedContext.cart.exception.CartErrorCode.CART_ITEM_NOT_FOUND;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartSchedulerTest {

    @Mock
    CartFacade cartFacade;

    @InjectMocks
    CartScheduler cartScheduler;

    @Test
    @DisplayName("한 건이 업무 예외로 실패해도 나머지 만료 항목을 계속 처리한다")
    void deleteDefaultPayment_continuesAfterBusinessException() {
        when(cartFacade.findExpiredItemIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new BusinessException(CART_ITEM_NOT_FOUND)).when(cartFacade).deleteExpiredItem(2L);

        cartScheduler.deleteDefaultPayment();

        verify(cartFacade).deleteExpiredItem(1L);
        verify(cartFacade).deleteExpiredItem(2L);
        verify(cartFacade).deleteExpiredItem(3L);
    }

    @Test
    @DisplayName("한 건이 예상하지 못한 예외로 실패해도 나머지 만료 항목을 계속 처리한다")
    void deleteDefaultPayment_continuesAfterUnexpectedException() {
        when(cartFacade.findExpiredItemIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("예상하지 못한 오류")).when(cartFacade).deleteExpiredItem(2L);

        cartScheduler.deleteDefaultPayment();

        verify(cartFacade, times(3)).deleteExpiredItem(anyLong());
    }

    @Test
    @DisplayName("만료 항목이 없으면 아무것도 삭제하지 않는다")
    void deleteDefaultPayment_noTargets() {
        when(cartFacade.findExpiredItemIds()).thenReturn(List.of());

        cartScheduler.deleteDefaultPayment();

        verify(cartFacade, never()).deleteExpiredItem(anyLong());
    }
}
