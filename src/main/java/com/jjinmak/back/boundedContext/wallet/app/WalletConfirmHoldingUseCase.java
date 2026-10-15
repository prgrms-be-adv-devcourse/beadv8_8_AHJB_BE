package com.jjinmak.back.boundedContext.wallet.app;


import com.jjinmak.back.boundedContext.wallet.domain.Holding;
import com.jjinmak.back.boundedContext.wallet.out.HoldingRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.HOLDING_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletConfirmHoldingUseCase {

    private final HoldingRepository holdingRepository;

    public void confirm(Long orderId) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        if (holding.isConfirmed()) return;

        holding.confirm();
    }
}
