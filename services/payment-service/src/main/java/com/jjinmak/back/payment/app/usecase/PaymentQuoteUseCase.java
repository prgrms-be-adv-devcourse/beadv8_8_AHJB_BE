package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.app.dto.PaymentQuoteDto;
import com.jjinmak.back.payment.domain.ChargePolicy;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 전 견적. 장바구니 화면에서 "예치금 n원 사용, 계좌이체로 m원 충전"을 미리 보여주기 위한 계산.
 * 충전 금액은 충전 정책(최소 충전 금액)을 적용한 값이다.
 */
@Service
@RequiredArgsConstructor
public class PaymentQuoteUseCase {

    private final WalletSupport walletSupport;
    private final ChargePolicy chargePolicy;

    public PaymentQuoteDto quote(Long holderId, long amount) {
        if (amount <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);
        }
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);
        long chargeAmount = chargePolicy.chargeAmountFor(Math.max(0L, amount - wallet.getBalance()));
        return PaymentQuoteDto.of(wallet.getBalance(), amount, chargeAmount,
                ChargePurpose.FOR_PAYMENT.requiredMethod().name(), chargePolicy.exceedsLimit(chargeAmount));
    }
}
