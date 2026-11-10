package com.jjinmak.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.app.support.ChargeRecoverySupport;
import com.jjinmak.payment.domain.Charge;
import com.jjinmak.payment.exception.PgApiException;
import com.jjinmak.payment.out.repository.ChargeRepository;
import com.jjinmak.payment.out.toss.TossPaymentsClient;
import com.jjinmak.payment.out.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.jjinmak.payment.domain.enums.ChargePurpose.FOR_PAYMENT;
import static com.jjinmak.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.payment.exception.PaymentErrorCode.CHARGE_NOT_FOUND;
import static com.jjinmak.payment.out.toss.TossPaymentsClient.NOT_FOUND_PAYMENT;

/**
 * REQUESTED 에 너무 오래 머문 Charge 정리. (PaymentScheduler → PaymentFacade → 여기)
 *
 * 토스에 pgOrderId 로 물어보고, 결과 반영은 ChargeRecoverySupport 가 한다 (웹훅 처리와 같은 정책).
 * 토스 인증 유효 시간(승인 가능 시간)은 10~30분이라, 임계값은 그보다 넉넉히 둔다.
 */
@Service
@RequiredArgsConstructor
public class ChargeRecoveryUseCase {

    public static final Duration STALE_AFTER = Duration.ofMinutes(40);

    private final ChargeRepository chargeRepository;
    private final TossPaymentsClient toss;
    private final ChargeRecoverySupport chargeRecoverySupport;

    /** REQUESTED 에 너무 오래 머문 건의 id */
    public List<Long> findStaleRequestedIds() {
        return chargeRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        REQUESTED, LocalDateTime.now().minus(STALE_AFTER))
                .stream().map(Charge::getId).toList();
    }

    /** 있으면 안 되는 상태. 홀딩과 같은 트랜잭션에서 USED 가 돼야 하므로 여기 걸리면 버그다 */
    public List<Long> findChargedButUnusedIds() {
        return chargeRepository.findTop100ByPurposeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        FOR_PAYMENT, CHARGED, LocalDateTime.now().minus(STALE_AFTER))
                .stream().map(Charge::getId).toList();
    }

    public void recover(Long chargeId) {
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        if (charge.getStatus() != REQUESTED) return;   // 조회 후 그 사이에 처리된 건

        TossPaymentResponse remote;
        try {
            remote = toss.findByOrderId(charge.getPgOrderId());
        } catch (PgApiException e) {
            if (e.isCode(NOT_FOUND_PAYMENT)) {
                chargeRecoverySupport.closeNotFound(charge);
                return;
            }
            throw e;   // 네트워크 오류 등: 다음 주기에 재시도
        }

        chargeRecoverySupport.recover(charge, remote);
    }
}
