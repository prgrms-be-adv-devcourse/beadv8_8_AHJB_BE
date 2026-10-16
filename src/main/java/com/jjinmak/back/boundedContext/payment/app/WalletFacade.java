package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.LedgerPageDto;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.payment.dto.HoldingSettlementDto;
import com.jjinmak.back.shared.payment.dto.WalletMemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트로 들어오는 지갑 내부 작업의 진입점 (PaymentEventListener 가 호출).
 * 회원 동기화·지갑 생성·환불·구매 확정·정산. 토스 호출이 없으므로 메서드 단위 @Transactional 로 충분하다.
 *
 * 충전·주문 결제처럼 토스 호출이 끼는 흐름은 PaymentFacade 가 담당한다.
 */
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

    @Transactional
    public void confirmHolding(Long orderId) {
        walletConfirmHoldingUseCase.confirm(orderId);
    }

    @Transactional
    public void releaseHolding(Long orderId) {
        walletReleaseHoldingUseCase.release(orderId);
    }

    @Transactional(readOnly = true)
    public HoldingSettlementDto getSettlement(Long orderId) {
        return walletGetSettlementUseCase.get(orderId);
    }

    @Transactional(readOnly = true)
    public LedgerPageDto getLedger(Long holderId, int page, int size) {
        return walletGetLedgerUseCase.get(holderId, page, size);
    }
}
