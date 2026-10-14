package com.jjinmak.back.boundedContext.wallet.in;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.dto.HoldDto;
import com.jjinmak.back.shared.wallet.port.WalletPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.WALLET_CONCURRENT_MODIFICATION;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletPortAdapter implements WalletPort {

    private static final int MAX_ATTEMPTS = 3;

    private final WalletFacade walletFacade;

    @Override
    public void charge(ChargeDto dto) {
        withRetry(() -> walletFacade.charge(dto));
    }

    @Override
    public void cancelCharge(ChargeDto dto) {
        withRetry(() -> walletFacade.cancelCharge(dto));
    }

    @Override
    public void revertCancelCharge(ChargeDto dto) {
        withRetry(() -> walletFacade.revertCancelCharge(dto));
    }

    @Override
    public long getBalance(Long holderId) {
        return walletFacade.getBalance(holderId);
    }

    @Override
    public void hold(HoldDto dto) {
        withRetry(() -> walletFacade.hold(dto));
    }

    private void withRetry(Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("지갑 낙관적 락 충돌, 재시도 소진 ({}회)", attempt, e);
                    throw new BusinessException(WALLET_CONCURRENT_MODIFICATION);
                }
                log.debug("지갑 낙관적 락 충돌, 재시도 {}/{}", attempt, MAX_ATTEMPTS);
            }

        }
    }
}
