package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 수동 충전 1단계: 결제창을 띄우기 전에 Charge(REQUESTED) 를 만들어 pgOrderId 와 금액을 확정한다.
 * 승인 단계에서 클라이언트가 보내온 amount 를 여기서 저장한 금액과 대조한다.
 */
@Service
@RequiredArgsConstructor
public class ChargeRequestUseCase {

    private final WalletSupport walletSupport;
    private final ChargeRepository chargeRepository;

    public ChargeSummaryDto requestManual(Long holderId, long amount) {
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);
        Charge charge = chargeRepository.save(Charge.requestManual(wallet, holderId, amount));
        return ChargeSummaryDto.from(charge);
    }
}
