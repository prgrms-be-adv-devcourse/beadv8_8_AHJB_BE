package com.jjinmak.payment.app.usecase;


import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.domain.Holding;
import com.jjinmak.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.jjinmak.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

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
