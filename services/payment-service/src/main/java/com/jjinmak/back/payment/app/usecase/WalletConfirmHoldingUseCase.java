package com.jjinmak.back.payment.app.usecase;


import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletConfirmHoldingUseCase {

    private final HoldingRepository holdingRepository;

    public void confirm(Long orderId) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        // 이미 구매 확정됐거나 그 뒤 정산까지 끝난 홀딩이면 같은 이벤트를 다시 받은 것이므로 넘어간다 (멱등).
        // 환불된 홀딩이면 아래 confirm() 에서 예외가 난다. 환불된 주문의 구매 확정은 중복이 아니라 잘못된 요청이다
        if (holding.isConfirmed() || holding.isReleased()) return;

        holding.confirm();
    }
}
