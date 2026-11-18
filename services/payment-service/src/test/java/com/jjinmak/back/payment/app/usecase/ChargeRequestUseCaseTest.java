package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.ChargePolicy;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수동 충전 요청. 금액은 충전 정책 범위 안이어야 하고, 응답으로 결제창을 열 결제수단(TRANSFER)을 알려 준다.
 */
class ChargeRequestUseCaseTest {

    private static final long HOLDER_ID = 1L;

    private ChargeRepository chargeRepository;
    private ChargeRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        WalletSupport walletSupport = mock(WalletSupport.class);
        when(walletSupport.getWalletByHolderId(HOLDER_ID)).thenReturn(new Wallet(null, WalletType.USER));

        chargeRepository = mock(ChargeRepository.class);
        when(chargeRepository.save(any(Charge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase = new ChargeRequestUseCase(walletSupport, chargeRepository, new ChargePolicy(10_000, 2_000_000));
    }

    @Test
    @DisplayName("범위 안의 금액이면 충전 건을 만들고 결제창을 계좌이체로 열라고 알려 준다")
    void requestWithinRange() {
        ChargeSummaryDto result = useCase.requestManual(HOLDER_ID, 10_000);

        assertThat(result.amount()).isEqualTo(10_000);
        assertThat(result.status()).isEqualTo(ChargeStatus.REQUESTED);
        assertThat(result.pgMethod()).isEqualTo("TRANSFER");
        assertThat(result.method()).isNull();                            // 실제 결제수단은 승인 뒤에 채워진다
        verify(chargeRepository).save(any(Charge.class));
    }

    @Test
    @DisplayName("최소 충전 금액보다 작으면 충전 건을 만들지 않고 CHARGE_AMOUNT_OUT_OF_RANGE")
    void belowMinimum() {
        assertThatThrownBy(() -> useCase.requestManual(HOLDER_ID, 9_999))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_AMOUNT_OUT_OF_RANGE);

        verify(chargeRepository, never()).save(any(Charge.class));
    }

    @Test
    @DisplayName("1회 최대 충전 금액보다 크면 충전 건을 만들지 않고 CHARGE_AMOUNT_OUT_OF_RANGE")
    void aboveMaximum() {
        assertThatThrownBy(() -> useCase.requestManual(HOLDER_ID, 2_000_001))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_AMOUNT_OUT_OF_RANGE);

        verify(chargeRepository, never()).save(any(Charge.class));
    }
}
