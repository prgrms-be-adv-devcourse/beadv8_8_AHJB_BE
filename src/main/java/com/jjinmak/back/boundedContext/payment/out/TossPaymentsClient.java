package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.exception.PgApiException;
import com.jjinmak.back.boundedContext.payment.out.dto.*;
import com.jjinmak.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.*;

/**
 * 토스페이먼츠 코어 API 어댑터. 우리가 쓰는 다섯 개만 감싼다.
 *
 *  - confirm          POST /v1/payments/confirm
 *  - cancel           POST /v1/payments/{paymentKey}/cancel      (전액 취소만)
 *  - findByPaymentKey GET  /v1/payments/{paymentKey}
 *  - findByOrderId    GET  /v1/payments/orders/{orderId}
 *  - findTransactions GET  /v1/transactions                       (대사용)
 *
 * 모든 메서드는 DB 트랜잭션 밖에서 호출해야 한다. (HTTP 대기 중 DB 커넥션·락을 잡고 있으면 안 됨)
 * 토스가 4xx/5xx 를 돌려주면 PgApiException(토스 code/message 포함), 네트워크 오류면 PG_UNAVAILABLE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    public static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";
    public static final String NOT_FOUND_PAYMENT = "NOT_FOUND_PAYMENT";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final DateTimeFormatter TOSS_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final RestClient tossPaymentsRestClient;

    /** 결제 승인. idempotencyKey 로 같은 요청이 두 번 가도 한 번만 승인된다 */
    public TossPaymentResponse confirm(String paymentKey, String pgOrderId, long amount, String idempotencyKey) {
        TossConfirmRequest body = new TossConfirmRequest(paymentKey, pgOrderId, amount);
        return call(() -> tossPaymentsRestClient.post()
                .uri("/v1/payments/confirm")
                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                .body(body)
                .exchange((req, res) -> readOrThrow(res, TossPaymentResponse.class, PG_APPROVE_FAILED)));
    }

    /** 전액 취소 */
    public TossPaymentResponse cancel(String paymentKey, String reason, String idempotencyKey) {
        TossCancelRequest body = new TossCancelRequest(reason);
        return call(() -> tossPaymentsRestClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                .body(body)
                .exchange((req, res) -> readOrThrow(res, TossPaymentResponse.class, PG_CANCEL_FAILED)));
    }

    public TossPaymentResponse findByPaymentKey(String paymentKey) {
        return call(() -> tossPaymentsRestClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .exchange((req, res) -> readOrThrow(res, TossPaymentResponse.class, PG_INQUIRY_FAILED)));
    }

    /** 우리가 만든 pgOrderId 로 조회. 승인 전(paymentKey 를 모를 때) 복구용 */
    public TossPaymentResponse findByOrderId(String pgOrderId) {
        return call(() -> tossPaymentsRestClient.get()
                .uri("/v1/payments/orders/{orderId}", pgOrderId)
                .exchange((req, res) -> readOrThrow(res, TossPaymentResponse.class, PG_INQUIRY_FAILED)));
    }

    /**
     * 기간 내 거래(승인/취소) 목록. transactionAt 기준. limit 기본 100, 최대 5000.
     * startingAfter 에 마지막 transactionKey 를 넣어 다음 페이지를 받는다.
     */
    public List<TossTransactionResponse> findTransactions(LocalDateTime start, LocalDateTime end,
                                                          String startingAfter, int limit) {
        return call(() -> tossPaymentsRestClient.get()
                .uri(builder -> {
                    builder.path("/v1/transactions")
                            .queryParam("startDate", TOSS_DATE.format(start))
                            .queryParam("endDate", TOSS_DATE.format(end))
                            .queryParam("limit", limit);
                    if (startingAfter != null) {
                        builder.queryParam("startingAfter", startingAfter);
                    }
                    return builder.build();
                })
                .exchange((req, res) -> readOrThrow(res,
                        new ParameterizedTypeReference<List<TossTransactionResponse>>() {}, PG_INQUIRY_FAILED)));
    }


    private <T> T readOrThrow(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res,
                              Class<T> type, ErrorCode onError) throws IOException {
        if (res.getStatusCode().is2xxSuccessful()) {
            return res.bodyTo(type);
        }
        throw toPgException(res, onError);
    }

    private <T> T readOrThrow(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res,
                              ParameterizedTypeReference<T> type, ErrorCode onError) throws IOException {
        if (res.getStatusCode().is2xxSuccessful()) {
            return res.bodyTo(type);
        }
        throw toPgException(res, onError);
    }

    private PgApiException toPgException(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res,
                                         ErrorCode onError) throws IOException {
        TossErrorResponse error = null;
        try {
            error = res.bodyTo(TossErrorResponse.class);
        } catch (Exception ignored) {
            // 본문이 JSON 이 아니거나 비어 있음
        }
        String code = error == null || error.code() == null ? "HTTP_" + res.getStatusCode().value() : error.code();
        String message = error == null ? null : error.message();
        log.warn("토스 API 오류 status={} code={} message={}", res.getStatusCode(), code, message);
        return new PgApiException(onError, code, message);
    }

    private <T> T call(Call<T> call) {
        try {
            return call.run();
        } catch (PgApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("토스 API 호출 실패 (네트워크/직렬화)", e);
            throw new PgApiException(PG_UNAVAILABLE, "NETWORK", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Call<T> {
        T run();
    }
}
