package com.jjinmak.back.settlement.app;

import com.jjinmak.back.settlement.domain.SettlementOutbox;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import com.jjinmak.contracts.settlement.domain.SettlementStatus;
import com.jjinmak.back.settlement.app.SettlementRequestPayoutUseCase;
import com.jjinmak.back.settlement.domain.Settlement;
import com.jjinmak.back.settlement.domain.SettlementPayoutResult;
import com.jjinmak.back.settlement.in.dto.SettlementDetailDto;
import com.jjinmak.back.settlement.in.dto.SettlementListDto;
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
    private final SettlementSaveProductUseCase settlementSaveProductUseCase;
    private final SettlementOutboxUseCase settlementOutboxUseCase;

    @Transactional
    public void saveProduct(ProductRegisteredEvent event) {
        settlementSaveProductUseCase.saveProduct(event);
    }

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
    public SettlementListDto getSettlements(Long memberId, SettlementStatus status, int page, int size) {
        return SettlementListDto.from(
                settlementGetSettlementsUseCase.getSettlements(memberId, status, page, size)
                        .map(Settlement::itemDto)
        );
    }

    @Transactional(readOnly = true)
    public SettlementDetailDto getSettlement(Long memberId, Long settlementId) {
        return settlementGetSettlementUseCase.getSettlement(memberId, settlementId).detailDto();
    }

    @Transactional(readOnly = true)
    public List<SettlementOutbox> findUnpublishedOutboxes() {
        return settlementOutboxUseCase.findUnpublished();
    }

    // 트랜잭션 없음: Kafka 응답을 기다리는 동안 DB 커넥션을 잡고 있지 않으려고
    public void sendOutbox(SettlementOutbox outbox) {
        settlementOutboxUseCase.send(outbox);
    }

    @Transactional
    public void markOutboxPublished(Long outboxId) {
        settlementOutboxUseCase.markPublished(outboxId);
    }
}