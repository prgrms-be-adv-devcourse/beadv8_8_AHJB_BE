package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.INSUFFICIENT_BALANCE;

/**
 * 예치금이 빠져나갈 때 "어떤 돈이 나가는지"를 충전 건 단위로 기록한다 (선입선출).
 *
 * 예치금 잔액 = 환불금 풀 + Σ(CHARGED 충전의 remainingAmount)
 *   · 환불금 풀 : 충전이 아닌 입금(환불). 별도 컬럼 없이 잔액 − Σremaining 으로 구한다
 *
 * 차감 순서
 *   ① earmarked (이 결제를 위해 방금 충전한 FOR_PAYMENT 건) 전액
 *   ② 환불금 풀
 *   ③ 나머지 CHARGED 충전을 오래된 순(chargedAt ASC)으로
 *
 * 유스케이스가 아니라 LedgerRecorder 처럼 유스케이스·지원 컴포넌트가 쓰는 지원 컴포넌트다.
 * 반드시 지갑에서 돈을 빼기 **전에**, 그리고 같은 트랜잭션 안에서 호출한다 (풀 계산이 현재 잔액을 쓰기 때문).
 * 동시성은 호출한 트랜잭션의 지갑 낙관적 락이 막는다. 충전 건을 소비하는 트랜잭션은 항상 지갑도 변경하므로
 * 두 요청이 겹치면 한쪽은 버전 충돌로 실패하고 재시도된다.
 *
 * 충전 취소는 자기 자신을 전액 되돌리는 것이라 이 순서를 타지 않는다 (Charge.markCanceled 가 remaining 을 0 으로).
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DepositConsumer {

    private final ChargeRepository chargeRepository;

    /** 일반 출금 (전액 예치금 결제 등). earmarked 없음 */
    public void consume(Wallet wallet, long amount) {
        consume(wallet, amount, null);
    }

    /**
     * @param earmarked 이 출금을 위해 방금 충전한 건. 가장 먼저 전액 소비된다. 없으면 null
     */
    public void consume(Wallet wallet, long amount, Charge earmarked) {
        if (amount <= 0) return;

        List<Charge> open = chargeRepository
                .findByWalletAndStatusAndRemainingAmountGreaterThanOrderByChargedAtAscIdAsc(wallet, CHARGED, 0L);

        long reserved = open.stream().mapToLong(Charge::getRemainingAmount).sum();
        long refundPool = wallet.getBalance() - reserved;          // 충전이 아닌 돈 (환불금)
        long left = amount;

        // ① 이 결제를 위한 충전부터
        if (earmarked != null) {
            left -= earmarked.consume(left);
        }
        // ② 환불금
        long fromPool = Math.min(left, Math.max(refundPool, 0L));
        left -= fromPool;
        // ③ 오래된 충전부터
        for (Charge charge : open) {
            if (left == 0) break;
            if (earmarked != null && charge.getId().equals(earmarked.getId())) continue;
            left -= charge.consume(left);
        }

        if (left > 0) {
            throw new BusinessException(INSUFFICIENT_BALANCE);      // 불변식이 깨졌거나 잔액 부족
        }
    }
}
