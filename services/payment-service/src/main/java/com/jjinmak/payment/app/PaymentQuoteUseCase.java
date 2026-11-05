package com.jjinmak.payment.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.app.dto.PaymentQuoteDto;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 전 견적. 장바구니 화면에서 "예치금 n원 사용, 카드 m원 결제"를 미리 보여주기 위한 계산.
 * 저장하는 것은 없다. 실제 결제 시점의 잔액으로 다시 계산되므로 안내용 값이다.
 */
@Service
@RequiredArgsConstructor
public class PaymentQuoteUseCase {

    private final WalletSupport walletSupport;

    public PaymentQuoteDto quote(Long holderId, long amount) {
        if (amount <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);
        }
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);
        return PaymentQuoteDto.of(wallet.getBalance(), amount);
    }
}
