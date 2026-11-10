package com.jjinmak.settlement.app;

import com.jjinmak.settlement.domain.Settlement;
import com.jjinmak.settlement.exception.SettlementErrorCode;
import com.jjinmak.settlement.domain.SettlementPayoutResult;
import com.jjinmak.settlement.out.SettlementRepository;
import com.jjinmak.settlement.out.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.settlement.domain.SettlementStatus;
import com.jjinmak.contracts.settlement.event.SettlementRequestedEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
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
     * 지갑과 다른 트랜잭션(Kafka)이므로 같은 메시지가 두 번 올 수 있고, 실패하면 3회 재시도 후 payment.payment-released-dlt에 보관된다.
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
