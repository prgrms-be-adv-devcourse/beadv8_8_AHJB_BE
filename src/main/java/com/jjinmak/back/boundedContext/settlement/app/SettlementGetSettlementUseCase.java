package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.boundedContext.settlement.domain.Settlement;
import com.jjinmak.back.boundedContext.settlement.domain.SettlementErrorCode;
import com.jjinmak.back.boundedContext.settlement.out.SettlementRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementGetSettlementUseCase {

    private final SettlementRepository settlementRepository;

    /**
     * 정산서를 조회한다. 해당 정산서의 판매자만 볼 수 있다.
     *
     * @param memberId     - 조회하는 회원 ID (로그인 회원)
     * @param settlementId - 정산서 ID
     * @return Settlement - 조회된 정산서
     */
    public Settlement getSettlement(Long memberId, Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        if (!settlement.isSeller(memberId)) {
            throw new BusinessException(SettlementErrorCode.NOT_SETTLEMENT_SELLER);
        }

        return settlement;
    }
}