package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.WithdrawalDto;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.Withdrawal;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.WithdrawalRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.WITHDRAWAL;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.NOTHING_TO_WITHDRAW;

/**
 * 예치금 전액 출금 (명목상). (파사드 트랜잭션 안에서 실행)
 *
 *   검증(잔액 > 0) → Withdrawal 생성 → 선입선출 소비(모든 충전 건 미사용 0) → 지갑 차감 + 원장 WITHDRAWAL → Withdrawal COMPLETED
 *
 * 실제 송금은 없다. 잔액을 0 으로 만들고 "등록 계좌로 빠져나갔다" 는 기록만 남긴다.
 * 정산금(settlementBalance)은 건드리지 않는다.
 * 전액 출금이므로 환불금 풀과 모든 충전 건의 남은 금액이 0 이 되고, 이후 그 충전들은 취소할 수 없다.
 * 동시성은 지갑 낙관적 락으로 막고, 재시도는 in/ 어댑터에서 WalletRetrySupport 로 감싼다.
 */
@Service
@RequiredArgsConstructor
public class WalletWithdrawUseCase {

    private final WalletSupport walletSupport;
    private final WithdrawalRepository withdrawalRepository;
    private final DepositConsumer depositConsumer;
    private final LedgerRecorder ledgerRecorder;

    public WithdrawalDto withdrawAll(Long holderId) {
        Wallet wallet = walletSupport.getWalletByHolderId(holderId);

        long amount = wallet.getBalance();
        if (amount <= 0) {
            throw new BusinessException(NOTHING_TO_WITHDRAW);
        }

        Withdrawal withdrawal = withdrawalRepository.save(Withdrawal.requestDeposit(wallet, holderId, amount));

        depositConsumer.consume(wallet, amount);                                   // 지갑 차감 전에
        ledgerRecorder.withdraw(wallet, WalletTransactionType.WITHDRAWAL, amount, WITHDRAWAL, withdrawal.getId());

        withdrawal.complete(LocalDateTime.now());
        return WithdrawalDto.from(withdrawal);
    }
}
