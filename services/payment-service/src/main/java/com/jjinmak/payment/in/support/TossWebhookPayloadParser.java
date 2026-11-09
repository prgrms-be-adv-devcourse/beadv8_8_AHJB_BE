package com.jjinmak.payment.in.support;

import com.jjinmak.payment.app.dto.TossWebhookReceiveDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 토스 웹훅 본문 파서. 토스 본문(외부 형식)을 수신 유스케이스가 받는 값(TossWebhookReceiveDto)으로 바꾼다.
 *
 * 하는 일
 *  - eventType, paymentKey, orderId 만 꺼낸다
 *  - 가상계좌 입금 확인용 secret 은 마스킹한 뒤 원문에 남긴다
 *  - 값이 엔티티 컬럼 길이를 넘으면 자른다. 넘친 채로 저장이 실패하면 토스가 같은 웹훅을 계속 재전송하기 때문이다
 *
 * 예외를 던지지 않는다. 파싱에 실패하거나 객체 형태가 아니면 eventType 을 UNKNOWN 으로 두고 원문 그대로 넘긴다.
 * 이런 건은 저장만 해 두고 처리 단계에서 IGNORED 로 거른다.
 *
 * 이벤트마다 본문 모양이 다르다
 *  - PAYMENT_STATUS_CHANGED : { eventType, createdAt, data: { paymentKey, orderId, status, ... } }
 *  - DEPOSIT_CALLBACK       : { createdAt, secret, status, transactionKey, orderId }  ← eventType 필드가 없다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossWebhookPayloadParser {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String DEPOSIT_CALLBACK = "DEPOSIT_CALLBACK";
    private static final String MASKED = "****";

    // 엔티티 컬럼 길이와 맞춘다
    private static final int EVENT_TYPE_MAX = 50;
    private static final int PAYMENT_KEY_MAX = 200;
    private static final int ORDER_ID_MAX = 64;

    private final ObjectMapper objectMapper;


    public TossWebhookReceiveDto parse(String body) {
        String rawBody = body == null ? "" : body;

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            log.warn("[토스 웹훅] 본문 파싱 실패. UNKNOWN 으로 저장합니다. reason={}", e.getOriginalMessage());
            return unknown(rawBody);
        }
        if (root == null || !root.isObject()) {
            return unknown(rawBody);
        }

        JsonNode data = root.path("data");
        String eventType = resolveEventType(root);
        String paymentKey = firstText(data.path("paymentKey"), root.path("paymentKey"));
        String orderId = firstText(data.path("orderId"), root.path("orderId"));

        return new TossWebhookReceiveDto(
                limit(eventType, EVENT_TYPE_MAX),
                limit(paymentKey, PAYMENT_KEY_MAX),
                limit(orderId, ORDER_ID_MAX),
                maskSecret((ObjectNode) root, rawBody)
        );
    }


    private TossWebhookReceiveDto unknown(String rawBody) {
        return new TossWebhookReceiveDto(UNKNOWN, null, null, rawBody);
    }

    /** eventType 이 있으면 그대로, 없고 secret 이 있으면 DEPOSIT_CALLBACK, 둘 다 없으면 UNKNOWN */
    private String resolveEventType(JsonNode root) {
        String eventType = text(root.path("eventType"));
        if (eventType != null) {
            return eventType;
        }
        if (root.has("secret")) {
            return DEPOSIT_CALLBACK;
        }
        return UNKNOWN;
    }

    /** secret 은 저장하지 않는다 */
    private String maskSecret(ObjectNode root, String originalBody) {
        if (!root.has("secret")) {
            return originalBody;
        }
        root.put("secret", MASKED);
        return objectMapper.writeValueAsString(root);
    }

    private String firstText(JsonNode first, JsonNode second) {
        String value = text(first);
        return value != null ? value : text(second);
    }

    private String text(JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString();
        return value.isBlank() ? null : value;
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
