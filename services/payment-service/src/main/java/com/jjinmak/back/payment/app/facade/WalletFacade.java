package com.jjinmak.back.payment.app.facade;

import com.jjinmak.back.payment.app.dto.*;
import com.jjinmak.back.payment.app.usecase.*;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.payment.dto.HoldingSettlementDto;
import com.jjinmak.contracts.payment.dto.WalletMemberDto;
import com.jjinmak.back.payment.app.dto.*;
import com.jjinmak.back.payment.app.usecase.*;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletFacade {
    private final WalletSyncMemberUseCase walletSyncMemberUseCase;
    private final WalletCreateUseCase walletCreateUseCase;
    private final WalletGetBalanceUseCase walletGetBalanceUseCase;
    private final WalletRefundOrderUseCase walletRefundOrderUseCase;
    private final WalletConfirmHoldingUseCase walletConfirmHoldingUseCase;
    private final WalletReleaseHoldingUseCase walletReleaseHoldingUseCase;
    private final WalletGetSettlementUseCase walletGetSettlementUseCase;
    private final WalletGetLedgerUseCase walletGetLedgerUseCase;
    private final WalletGetHoldingsUseCase walletGetHoldingsUseCase;
    private final WalletWithdrawUseCase walletWithdrawUseCase;
    private final WalletGetWithdrawalsUseCase walletGetWithdrawalsUseCase;
    private final PaymentCloseUseCase paymentCloseUseCase;

    @Transactional
    public WalletMember syncMember(MemberDto member) {
        return walletSyncMemberUseCase.syncMember(member);
    }

    @Transactional
    public void createWallet(WalletMemberDto holder) {
        walletCreateUseCase.createWallet(holder);
    }

    @Transactional
    public void refund(Long refundId, Long orderId, long amount) {
        walletRefundOrderUseCase.refund(refundId, orderId, amount);
    }

    @Transactional(readOnly = true)
    public long getBalance(Long holderId) {
        return walletGetBalanceUseCase.getBalance(holderId);
    }

    @Transactional(readOnly = true)
    public WalletBalanceDto getBalances(Long holderId) {
        return walletGetBalanceUseCase.getBalances(holderId);
    }

    @Transactional
    public void closePaymentByOrderGroupDeleted(Long orderGroupId) {
        paymentCloseUseCase.closeOnOrderDeleted(orderGroupId);
    }

    @Transactional
    public void confirmHolding(Long orderId) {
        walletConfirmHoldingUseCase.confirm(orderId);
    }

    @Transactional
    public void releaseHolding(Long orderId) {
        walletReleaseHoldingUseCase.release(orderId);
    }

    @Transactional
    public void releaseHolding(Long orderId, Long expectedSettlementAmount, Long expectedFeeAmount) {
        walletReleaseHoldingUseCase.release(orderId, expectedSettlementAmount, expectedFeeAmount);
    }

    @Transactional(readOnly = true)
    public HoldingSettlementDto getSettlement(Long orderId) {
        return walletGetSettlementUseCase.get(orderId);
    }

    @Transactional(readOnly = true)
    public LedgerPageDto getLedger(Long holderId, WalletAccount account, WalletTransactionType type, int page, int size) {
        return walletGetLedgerUseCase.get(holderId, account, type, page, size);
    }

    @Transactional
    public WithdrawalDto withdrawAll(Long holderId) {
        return walletWithdrawUseCase.withdrawAll(holderId);
    }

    @Transactional(readOnly = true)
    public PageDto<WithdrawalDto> listWithdrawals(Long holderId, int page, int size) {
        return walletGetWithdrawalsUseCase.list(holderId, page, size);
    }

    @Transactional(readOnly = true)
    public HoldingPageDto getHoldings(Long holderId, HoldingRole role, HoldingStatus status, int page, int size) {
        return walletGetHoldingsUseCase.get(holderId, role, status, page, size);
    }
}
