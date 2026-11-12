package com.jjinmak.payment.app.usecase;

import com.jjinmak.payment.app.dto.TossWebhookReceiveDto;
import com.jjinmak.payment.domain.TossWebhookEvent;
import com.jjinmak.payment.out.repository.TossWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class TossWebhookReceiveUseCase {

    private final TossWebhookEventRepository tossWebhookEventRepository;

    // 토스 웹훅 수신 시 저장만 하고 200을 돌려줌. 웹훅 이벤트 처리는 스케줄러를 통해 진행
    public Long receive(TossWebhookReceiveDto dto) {
        TossWebhookEvent event = TossWebhookEvent.receive(
                dto.eventType(),
                dto.paymentKey(),
                dto.orderId(),
                dto.rawBody()
        );
        return tossWebhookEventRepository.save(event).getId();
    }
}
