package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementErrorCode;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementPayoutResult;
import com.jjinmak.back.boundedContext.settlement.out.SettlementRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import com.jjinmak.back.shared.settlement.event.SettlementRequestedEvent;
import com.jjinmak.back.shared.wallet.event.WalletReleasedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementCompleteSettlementUseCase {

    private final SettlementRepository settlementRepository;

    /**
     * 지갑의 지급 완료 이벤트를 받아 정산서를 정산 완료(COMPLETED)로 바꾼다.
     * 지갑 지급과 같은 트랜잭션에서 실행되므로, 여기서 실패하면 지급도 롤백된다.
     * 지갑은 이미 지급한 주문이 다시 요청되면 완료 이벤트만 다시 보내므로, 이미 완료된 정산서는 건너뛴다.
     *
     * @param event - 주문 ID, 판매자 ID, 홀딩 총액, 수수료, 정산 금액, 지급 시각
     */
    public void completeSettlement(WalletReleasedEvent event) {
        Settlement settlement = settlementRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        if (settlement.isCompleted()) {
            return;
        }

        settlement.complete(event.releasedAt());
    }
}