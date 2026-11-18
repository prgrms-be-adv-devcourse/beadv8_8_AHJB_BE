package com.jjinmak.back.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_IN_PROGRESS;

/**
 * 결제 요청 · 승인 · 닫기는 Payment 행을 FOR UPDATE 로 잠근다 (PaymentRepository.findBy...ForUpdate).
 * 다른 흐름이 락을 쥐고 있어(대개 토스 승인 호출 중) 기다리다 시간이 넘으면 PessimisticLockingFailureException 이 나는데,
 * 이를 "처리 중이니 잠시 후 다시 시도" (PAYMENT_IN_PROGRESS) 로 바꾼다.
 *
 * 락 대기 실패는 트랜잭션을 못 쓰게 만들므로, @Transactional 메서드 바깥(파사드를 부르는 쪽)에서 이걸로 감싼다.
 */
@Slf4j
@Component
public class PaymentLockWaitSupport {

    public <T> T orInProgress(Supplier<T> action) {
        try {
            return action.get();
        } catch (PessimisticLockingFailureException e) {
            log.warn("결제 락 대기 시간 초과: {}", e.getMessage());
            throw new BusinessException(PAYMENT_IN_PROGRESS);
        }
    }
}
