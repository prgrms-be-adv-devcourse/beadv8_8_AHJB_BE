package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.CHARGE_NOT_FOUND;

/**
 * 토스 승인이 실패했을 때 Charge 를 ABORTED 로 남기는 지원 컴포넌트.
 *
 * 승인 실패는 예외로 끝나고 바깥 트랜잭션(파사드)은 롤백되므로, 실패 사유를 남기려면 별도 트랜잭션(REQUIRES_NEW)이 필요하다.
 *
 * 주의: 바깥 트랜잭션이 아직 Charge 행을 수정(flush)하지 않은 시점에서만 호출할 것.
 *       이미 수정했다면 행 락 때문에 이 트랜잭션이 기다리다 실패한다. (그 경우는 복구 스케줄러가 정리한다)
 */
@Component
@RequiredArgsConstructor
public class ChargeFailureRecorder {

    private final ChargeRepository chargeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAborted(Long chargeId, String failureCode, String failureMessage) {
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        charge.markAborted(LocalDateTime.now(), failureCode, failureMessage);
    }
}
