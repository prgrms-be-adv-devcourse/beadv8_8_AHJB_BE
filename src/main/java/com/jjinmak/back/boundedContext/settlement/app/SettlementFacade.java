package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementPayoutResult;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import com.jjinmak.back.shared.settlement.dto.SettlementListDto;
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
    private final SettlementGetSettlementsUseCase settlementGetSettlementsUseCase;

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

    @Transactional(readOnly = true)
    public SettlementListDto getSettlements(Long memberId, SettlementStatus status, int page, int size) {
        return SettlementListDto.from(
                settlementGetSettlementsUseCase.getSettlements(memberId, status, page, size)
                        .map(Settlement::itemDto)
        );
    }
}
