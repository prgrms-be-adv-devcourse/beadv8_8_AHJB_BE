package com.jjinmak.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.HoldingSettlementDto;
import com.jjinmak.payment.domain.Holding;
import com.jjinmak.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

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
