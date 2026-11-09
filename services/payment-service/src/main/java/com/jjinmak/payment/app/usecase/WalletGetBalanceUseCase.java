package com.jjinmak.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.app.support.WalletSupport;
import com.jjinmak.payment.app.dto.WalletBalanceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.payment.exception.WalletErrorCode.WALLET_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WalletGetBalanceUseCase {
    private final WalletSupport walletSupport;

    /** 예치금 잔액만. 결제 가능 금액이 필요한 곳(주문 포트, 견적)에서 사용 */
    public long getBalance(Long holderId) {
        return walletSupport.findWalletByHolderId(holderId)
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND))
                .getBalance();
    }

    /** 예치금 + 정산금. 내 지갑 화면용 */
    public WalletBalanceDto getBalances(Long holderId) {
        return walletSupport.findWalletByHolderId(holderId)
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND))
                .toDto();
    }
}
