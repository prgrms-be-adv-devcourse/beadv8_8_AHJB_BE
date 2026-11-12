package com.jjinmak.payment.app.usecase;

import com.jjinmak.payment.app.support.WalletSupport;
import com.jjinmak.payment.app.dto.HoldingPageDto;
import com.jjinmak.payment.app.dto.HoldingRole;
import com.jjinmak.payment.app.dto.HoldingSummaryDto;
import com.jjinmak.payment.app.dto.PageDto;
import com.jjinmak.payment.domain.Holding;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.domain.enums.HoldingStatus;
import com.jjinmak.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jjinmak.payment.domain.enums.HoldingStatus.CONFIRMED;
import static com.jjinmak.payment.domain.enums.HoldingStatus.HELD;

@Service
@RequiredArgsConstructor
public class WalletGetHoldingsUseCase {

    private static final List<HoldingStatus> OPEN = List.of(HELD, CONFIRMED);
    private static final List<HoldingStatus> SETTLEABLE = List.of(CONFIRMED);

    private final WalletSupport walletSupport;
    private final HoldingRepository holdingRepository;

    public HoldingPageDto get(Long holderId, HoldingRole role, HoldingStatus status, int page, int size) {
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);
        PageRequest pageRequest = PageDto.latest(page, size);

        Page<Holding> holdings;
        long heldTotal;
        long settleableTotal;

        if (role == HoldingRole.SELLER) {
            holdings = status == null
                    ? holdingRepository.findBySellerWallet(wallet, pageRequest)
                    : holdingRepository.findBySellerWalletAndStatus(wallet, status, pageRequest);
            heldTotal = holdingRepository.sumSettlementBySellerWalletAndStatusIn(wallet, OPEN);
            settleableTotal = holdingRepository.sumSettlementBySellerWalletAndStatusIn(wallet, SETTLEABLE);
        } else {
            holdings = status == null
                    ? holdingRepository.findByBuyerWallet(wallet, pageRequest)
                    : holdingRepository.findByBuyerWalletAndStatus(wallet, status, pageRequest);
            heldTotal = holdingRepository.sumAmountByBuyerWalletAndStatusIn(wallet, OPEN);
            settleableTotal = holdingRepository.sumAmountByBuyerWalletAndStatusIn(wallet, SETTLEABLE);
        }

        return HoldingPageDto.of(role, heldTotal, settleableTotal, holdings.map(h -> HoldingSummaryDto.from(h, role)));
    }
}
