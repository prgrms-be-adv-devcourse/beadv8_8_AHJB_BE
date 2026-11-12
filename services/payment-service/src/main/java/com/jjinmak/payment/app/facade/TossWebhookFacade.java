package com.jjinmak.payment.app.facade;

import com.jjinmak.payment.app.dto.TossWebhookReceiveDto;
import com.jjinmak.payment.app.usecase.TossWebhookProcessUseCase;
import com.jjinmak.payment.app.usecase.TossWebhookReceiveUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 토스 웹훅 파사드.
 *
 * 수신(저장)과 처리(토스 조회 → 결제·충전 반영)를 나눠서 갖는다.
 * 수신은 토스에 10초 안에 응답해야 하므로 저장 한 번으로 끝나는 짧은 트랜잭션이다.
 * 처리는 웹훅 1건마다 별도 트랜잭션이며, 예외로 롤백된 건은 recordFailure 로 실패를 따로 남긴다.
 */
@Service
@RequiredArgsConstructor
public class TossWebhookFacade {

    private final TossWebhookReceiveUseCase tossWebhookReceiveUseCase;
    private final TossWebhookProcessUseCase tossWebhookProcessUseCase;

    @Transactional
    public Long receive(TossWebhookReceiveDto dto) {
        return tossWebhookReceiveUseCase.receive(dto);
    }

    @Transactional(readOnly = true)
    public List<Long> findProcessableIds() {
        return tossWebhookProcessUseCase.findProcessableIds();
    }

    @Transactional
    public void process(Long eventId) {
        tossWebhookProcessUseCase.process(eventId);
    }

    @Transactional
    public void recordFailure(Long eventId, String error) {
        tossWebhookProcessUseCase.recordFailure(eventId, error);
    }
}
