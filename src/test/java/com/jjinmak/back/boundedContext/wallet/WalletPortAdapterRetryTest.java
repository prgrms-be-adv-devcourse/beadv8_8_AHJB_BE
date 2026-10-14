package com.jjinmak.back.boundedContext.wallet;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.port.WalletPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.WALLET_CONCURRENT_MODIFICATION;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WalletPortAdapter 의 재시도 동작. 실제 충돌 대신 Facade 가 낙관적 락 예외를 던지도록 강제한다.
 */
@SpringBootTest
class WalletPortAdapterRetryTest {

    @Autowired private WalletPort walletPort;

    @MockitoSpyBean private WalletFacade walletFacade;

    @Test
    @DisplayName("낙관적 락 충돌이 두 번 나고 세 번째에 성공하면 정상 처리된다")
    void retry_succeedsOnThirdAttempt() {
        ChargeDto dto = new ChargeDto(1L, 1_000L, 1L);
        doThrow(new OptimisticLockingFailureException("1"))
                .doThrow(new OptimisticLockingFailureException("2"))
                .doNothing()
                .when(walletFacade).charge(any());

        walletPort.charge(dto);

        verify(walletFacade, times(3)).charge(dto);
    }

    @Test
    @DisplayName("재시도를 전부 소진하면 WALLET_CONCURRENT_MODIFICATION 으로 바뀌어 올라온다")
    void retry_exhausted_throwsBusinessException() {
        ChargeDto dto = new ChargeDto(1L, 1_000L, 1L);
        doThrow(new OptimisticLockingFailureException("충돌"))
                .when(walletFacade).charge(any());

        assertThatThrownBy(() -> walletPort.charge(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(WALLET_CONCURRENT_MODIFICATION);

        verify(walletFacade, times(3)).charge(dto);
    }
}