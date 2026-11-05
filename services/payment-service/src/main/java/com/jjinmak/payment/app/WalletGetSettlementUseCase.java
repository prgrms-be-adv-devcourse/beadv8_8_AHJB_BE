package com.jjinmak.payment.app;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.HoldingSettlementDto;
import com.jjinmak.payment.domain.Holding;
import com.jjinmak.payment.out.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

/**
 * 주문 1건의 정산 정보 조회. 정산 컨텍스트가 정산 전에 수수료·정산액을 확인할 때 쓴다.
 * Holding 에 홀딩 시점으로 고정된 값을 그대로 돌려주므로, 이후 수수료 정책이 바뀌어도 결과가 달라지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WalletGetSettlementUseCase {

    private final HoldingRepository holdingRepository;

    public HoldingSettlementDto get(Long orderId) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        return new HoldingSettlementDto(
                holding.getOrderId(),
                holding.getBuyerWallet().getHolder().getId(),
                holding.getSellerWallet().getHolder().getId(),
                holding.getWinningPrice(),
                holding.getDeliveryFee(),
                holding.getAmount(),
                holding.getFeeAmount(),
                holding.getSettlementAmount(),
                holding.getStatus().name()
        );
    }
}
