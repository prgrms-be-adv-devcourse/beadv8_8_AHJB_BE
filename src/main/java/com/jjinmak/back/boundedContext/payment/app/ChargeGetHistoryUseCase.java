package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.app.dto.PageDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargePurpose.MANUAL;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.CHARGE_NOT_FOUND;

/**
 * 내 충전 내역 목록과 상세.
 * 목록은 수동 충전(MANUAL)만 보여준다. 주문 부족분 충전(FOR_PAYMENT)은 해당 결제의 상세에서 보여주는 게 사용자에게 자연스럽다.
 * 상세는 용도와 무관하게 내 것이면 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ChargeGetHistoryUseCase {

    private final ChargeRepository chargeRepository;

    public PageDto<ChargeSummaryDto> listManual(Long holderId, int page, int size) {
        return PageDto.of(chargeRepository.findByHolderIdAndPurpose(holderId, MANUAL, PageDto.latest(page, size))
                .map(ChargeSummaryDto::from));
    }

    public ChargeSummaryDto detail(Long holderId, Long chargeId) {
        Charge charge = chargeRepository.findByIdAndHolderId(chargeId, holderId)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));
        return ChargeSummaryDto.from(charge);
    }
}
