package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementPayoutResult;
import com.jjinmak.back.boundedContext.settlement.out.SettlementRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import com.jjinmak.back.shared.settlement.event.SettlementRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementRequestPayoutUseCase {

    private final SettlementRepository settlementRepository;
    private final EventPublisher eventPublisher;

    /**
     * 지급일이 된(지급일 ≤ 기준일) PENDING 정산서 id를 조회한다.
     * @param today - 배치 기준일
     * @return List<Long> - 지급 대상 정산서 id
     */
    public List<Long> findPayoutTargetIds(LocalDate today) {
        return settlementRepository.findPayoutTargetIds(SettlementStatus.PENDING, today);
    }

    /**
     * 정산서 1건의 지급을 지갑에 요청한다 (정산 요청 이벤트 발행). 상태는 바꾸지 않고,
     * 지갑의 지급 완료 이벤트(WalletReleasedEvent)를 받을 때 COMPLETED가 된다.
     * 이미 완료됐거나 지급일 전이면 건너뛴다. 같은 정산서를 다시 요청해도 지갑이 한 번만 지급한다.
     *
     * @param settlementId - 정산서 ID
     * @param today        - 배치 기준일
     * @return SettlementPayoutResult - REQUESTED(요청함) / SKIPPED(건너뜀)
     */
    public SettlementPayoutResult requestPayout(Long settlementId, LocalDate today) {
        Settlement settlement = settlementRepository.findById(settlementId).orElse(null);

        if (settlement == null || !settlement.isPayable(today)) {
            return SettlementPayoutResult.SKIPPED;
        }

        eventPublisher.publish(new SettlementRequestedEvent(settlement.dto()));

        return SettlementPayoutResult.REQUESTED;
    }
}