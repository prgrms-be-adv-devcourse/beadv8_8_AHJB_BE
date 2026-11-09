package com.jjinmak.payment.app;

import com.jjinmak.payment.app.dto.TossWebhookReceiveDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토스 웹훅 파사드.
 *
 * 수신(저장)과 처리(토스 조회 → 결제·충전 반영)를 나눠서 갖는다.
 * 수신은 토스에 10초 안에 응답해야 하므로 저장 한 번으로 끝나는 짧은 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
public class TossWebhookFacade {

    private final TossWebhookReceiveUseCase tossWebhookReceiveUseCase;

    /** 웹훅 저장. 반환값은 저장된 이벤트 id */
    @Transactional
    public Long receive(TossWebhookReceiveDto dto) {
        return tossWebhookReceiveUseCase.receive(dto);
    }
}
