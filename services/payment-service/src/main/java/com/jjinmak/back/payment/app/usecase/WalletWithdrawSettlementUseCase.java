package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.WithdrawalDto;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.Withdrawal;
import com.jjinmak.back.payment.out.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.WITHDRAWAL;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.SETTLEMENT_WITHDRAWAL;
import static com.jjinmak.back.payment.exception.WalletErrorCode.NOTHING_SETTLEMENT_TO_WITHDRAW;

/**
 * 정산금 전액 출금 (명목상). (파사드 트랜잭션 안에서 실행)
 *
 *   검증(정산금 > 0) → Withdrawal(account = SETTLEMENT) 생성 → 정산금 차감 + 원장 SETTLEMENT_WITHDRAWAL → Withdrawal COMPLETED
 *
 * 실제 송금은 없다. 정산금을 0 으로 만들고 "등록 계좌로 빠져나갔다" 는 기록만 남긴다.
 * 예치금(balance)과 포인트는 건드리지 않는다. 정산금은 충전 건과 관계없으므로 선입선출 소비도 하지 않는다.
 * 동시성은 지갑 낙관적 락으로 막고, 재시도는 in/ 어댑터에서 WalletRetrySupport 로 감싼다.
 */
@Service
@RequiredArgsConstructor
public class WalletWithdrawSettlementUseCase {

    private final WalletSupport walletSupport;
    private final WithdrawalRepository withdrawalRepository;
    private final LedgerRecorder ledgerRecorder;

    public WithdrawalDto withdrawAll(Long holderId) {
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);

        long amount = wallet.getSettlementBalance();
        if (amount <= 0) {
            throw new BusinessException(NOTHING_SETTLEMENT_TO_WITHDRAW);
        }

        Withdrawal withdrawal = withdrawalRepository.save(Withdrawal.requestSettlement(wallet, holderId, amount));

        ledgerRecorder.withdrawSettlement(wallet, SETTLEMENT_WITHDRAWAL, amount, WITHDRAWAL, withdrawal.getId());

        withdrawal.complete(LocalDateTime.now());
        return WithdrawalDto.from(withdrawal);
    }
}
