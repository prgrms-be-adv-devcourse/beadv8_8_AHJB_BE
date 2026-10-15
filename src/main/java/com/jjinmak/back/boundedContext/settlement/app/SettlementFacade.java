package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementPayoutResult;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import com.jjinmak.back.shared.settlement.dto.SettlementDetailDto;
import com.jjinmak.back.shared.settlement.dto.SettlementListDto;
import com.jjinmak.back.shared.wallet.event.WalletReleasedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementFacade {

    private final SettlementCreateSettlementUseCase settlementCreateSettlementUseCase;
    private final SettlementRequestPayoutUseCase settlementRequestPayoutUseCase;
    private final SettlementCompleteSettlementUseCase settlementCompleteSettlementUseCase;
    private final SettlementGetSettlementsUseCase settlementGetSettlementsUseCase;
    private final SettlementGetSettlementUseCase settlementGetSettlementUseCase;

    @Transactional
    public void createSettlement(OrderConfirmPurchaseEvent event) {
        settlementCreateSettlementUseCase.createSettlement(event);
    }

    @Transactional(readOnly = true)
    public List<Long> findPayoutTargetIds(LocalDate today) {
        return settlementRequestPayoutUseCase.findPayoutTargetIds(today);
    }

    @Transactional
    public SettlementPayoutResult requestPayout(Long settlementId, LocalDate today) {
        return settlementRequestPayoutUseCase.requestPayout(settlementId, today);
    }

    @Transactional
    public void completeSettlement(WalletReleasedEvent event) {
        settlementCompleteSettlementUseCase.completeSettlement(event);
    }

    @Transactional(readOnly = true)
    public SettlementDetailDto getSettlement(Long memberId, Long settlementId) {
        return settlementGetSettlementUseCase.getSettlement(memberId, settlementId).detailDto();
    }
}
