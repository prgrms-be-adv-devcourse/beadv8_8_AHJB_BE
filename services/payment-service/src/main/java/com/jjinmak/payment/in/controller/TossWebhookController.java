package com.jjinmak.payment.in.controller;

import com.jjinmak.payment.app.facade.TossWebhookFacade;
import com.jjinmak.payment.app.dto.TossWebhookReceiveDto;
import com.jjinmak.payment.in.support.TossWebhookPayloadParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠 웹훅 수신.
 *
 * 토스 서버가 직접 호출하므로 회원 헤더가 없다. 보안 설정에서 이 경로를 permitAll 로 열어야 한다.
 * 본문은 위조될 수 있으므로 여기서는 저장만 하고, 실제 상태는 처리 단계에서 토스 조회 API 로 다시 확인한다.
 * 본문 해석과 secret 마스킹은 TossWebhookPayloadParser 가 맡는다.
 *
 * 응답 규칙
 *  - 저장에 성공하면 200. 토스는 10초 안에 200 을 받아야 성공으로 본다
 *  - 저장에 실패하면 예외가 그대로 올라가 5xx → 토스가 나중에 다시 보낸다
 *  - 본문 파싱에 실패해도 UNKNOWN 으로 저장하고 200. 다시 받아도 결과가 같기 때문이다
 */
@Slf4j
@Tag(name = "토스 웹훅", description = "토스페이먼츠가 호출하는 웹훅 수신 API (프론트에서 호출하지 않음)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/webhooks")
public class TossWebhookController {

    private final TossWebhookFacade tossWebhookFacade;
    private final TossWebhookPayloadParser tossWebhookPayloadParser;


    @Operation(
            summary = "토스 웹훅 수신",
            description = "토스페이먼츠가 결제 상태 변경, 가상계좌 입금 등을 알릴 때 호출합니다. 받은 본문을 저장만 하고 200 을 돌려줍니다."
    )
    @PostMapping("/toss")
    public ResponseEntity<Void> receive(@RequestBody(required = false) String body) {
        TossWebhookReceiveDto dto = tossWebhookPayloadParser.parse(body);
        Long eventId = tossWebhookFacade.receive(dto);

        log.info("[토스 웹훅] 수신 저장 eventId={}, eventType={}, orderId={}", eventId, dto.eventType(), dto.orderId());
        return ResponseEntity.ok().build();
    }
}
