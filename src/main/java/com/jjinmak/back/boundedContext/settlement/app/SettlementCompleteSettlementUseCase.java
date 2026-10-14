package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementPayoutResult;
import com.jjinmak.back.boundedContext.settlement.out.SettlementRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.settlement.domain.SettlementStatus;
import com.jjinmak.back.shared.settlement.event.SettlementCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementCompleteSettlementUseCase {

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
     * 정산서 1건을 정산 완료로 바꾸고 정산 완료 이벤트를 발행한다.
     * 지갑이 같은 트랜잭션에서 이벤트를 받아 지급하므로, 지급이 실패하면 정산서도 롤백되어 PENDING으로 남는다.
     * 이미 처리됐거나 지급일 전이면 건너뛴다 (배치를 다시 실행해도 안전).
     *
     * @param settlementId - 정산서 ID
     * @param today        - 배치 기준일
     * @return SettlementPayoutResult - COMPLETED(완료) / SKIPPED(건너뜀)
     */
    public SettlementPayoutResult completeSettlement(Long settlementId, LocalDate today) {
        Settlement settlement = settlementRepository.findById(settlementId).orElse(null);

        if (settlement == null || !settlement.isPayable(today)) {
            return SettlementPayoutResult.SKIPPED;
        }

        settlement.complete();
        settlementRepository.flush();

        eventPublisher.publish(new SettlementCompletedEvent(settlement.dto()));

        return SettlementPayoutResult.COMPLETED;
    }

}
