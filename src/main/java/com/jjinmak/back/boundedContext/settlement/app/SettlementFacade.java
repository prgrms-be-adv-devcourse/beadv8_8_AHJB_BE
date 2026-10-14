package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.SettlementPayoutResult;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementFacade {

    private final SettlementCreateSettlementUseCase settlementCreateSettlementUseCase;
    private final SettlementCompleteSettlementUseCase settlementCompleteSettlementUseCase;

    @Transactional
    public void createSettlement(OrderConfirmPurchaseEvent event) {
        settlementCreateSettlementUseCase.createSettlement(event);
    }

    @Transactional(readOnly = true)
    public List<Long> findPayoutTargetIds(LocalDate today) {
        return settlementCompleteSettlementUseCase.findPayoutTargetIds(today);
    }

    @Transactional
    public SettlementPayoutResult completeSettlement(Long settlementId, LocalDate today) {
        return settlementCompleteSettlementUseCase.completeSettlement(settlementId, today);
    }
}
