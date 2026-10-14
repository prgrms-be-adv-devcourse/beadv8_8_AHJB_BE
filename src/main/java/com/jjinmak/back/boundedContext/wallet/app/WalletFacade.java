package com.jjinmak.back.boundedContext.wallet.app;

import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.dto.HoldDto;
import com.jjinmak.back.shared.wallet.dto.WalletMemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletFacade {
    private final WalletSyncMemberUseCase walletSyncMemberUseCase;
    private final WalletCreateUseCase walletCreateUseCase;
    private final WalletChargeUseCase walletChargeUseCase;
    private final WalletChargeCancelUseCase walletChargeCancelUseCase;
    private final WalletCreateHoldingUseCase walletCreateHoldingUseCase;
    private final WalletGetBalanceUseCase walletGetBalanceUseCase;
    private final WalletRefundOrderUseCase walletRefundOrderUseCase;

    @Transactional
    public WalletMember syncMember(MemberDto member) {
        return walletSyncMemberUseCase.syncMember(member);
    }

    @Transactional
    public void createWallet(WalletMemberDto holder) {
        walletCreateUseCase.createWallet(holder);
    }

    @Transactional
    public void charge(ChargeDto dto) {
        walletChargeUseCase.charge(dto);
    }

    @Transactional
    public void cancelCharge(ChargeDto dto) {
        walletChargeCancelUseCase.cancelCharge(dto);
    }

    @Transactional
    public void revertCancelCharge(ChargeDto dto) {
        walletChargeCancelUseCase.revertCancelCharge(dto);
    }

    @Transactional
    public void hold(HoldDto dto) {
        walletCreateHoldingUseCase.hold(dto);
    }

    @Transactional
    public void refund(Long refundId, Long orderId, long amount) {
        walletRefundOrderUseCase.refund(refundId, orderId, amount);
    }

    @Transactional(readOnly = true)
    public long getBalance(Long holderId) {
        return walletGetBalanceUseCase.getBalance(holderId);
    }
}
