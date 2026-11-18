package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.HoldingPageDto;
import com.jjinmak.back.boundedContext.payment.app.dto.HoldingRole;
import com.jjinmak.back.boundedContext.payment.app.dto.HoldingSummaryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.payment.out.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus.CONFIRMED;
import static com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus.HELD;

/**
 * 내 홀딩 조회.
 *  - BUYER  : 내가 결제한 돈이 어느 주문에 묶여 있는지 (환불·정산 전). 합계는 묶인 금액(amount)
 *  - SELLER : 내가 받을 정산 예정 금액. 합계는 정산액(amount − 수수료)
 * status 를 주면 그 상태만, 없으면 전체(정산·환불 끝난 것 포함)를 최신순으로 돌려준다.
 */
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
