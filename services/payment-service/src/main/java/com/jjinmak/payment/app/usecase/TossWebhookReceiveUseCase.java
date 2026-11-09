package com.jjinmak.payment.app.usecase;

import com.jjinmak.payment.app.dto.TossWebhookReceiveDto;
import com.jjinmak.payment.domain.TossWebhookEvent;
import com.jjinmak.payment.out.repository.TossWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 토스 웹훅 수신. (파사드 트랜잭션 안에서 실행)
 *
 *   수신값 → TossWebhookEvent(RECEIVED) 저장 → id 반환
 *
 * 토스에는 10초 안에 200 을 돌려줘야 하므로 여기서는 저장만 한다.
 * 토스 조회와 결제·충전 상태 반영은 처리 유스케이스가 따로 한다.
 *
 * 값 검증은 하지 않는다. 저장이 실패하면 토스가 같은 웹훅을 계속 다시 보내기 때문에,
 * 형식이 이상하거나 값이 빠진 건도 일단 저장하고 처리 단계에서 IGNORED 로 거른다.
 * 웹훅 본문에 eventId 가 없어 같은 내용이 여러 번 저장될 수 있으며, 중복도 처리 단계에서 거른다.
 */
@Service
@RequiredArgsConstructor
public class TossWebhookReceiveUseCase {

    private final TossWebhookEventRepository tossWebhookEventRepository;

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
