package com.jjinmak.order.out;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.order.out.payment.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

import static com.jjinmak.order.exception.OrderErrorCode.PAYMENT_REQUEST_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentRequestTest {

    static final String PAYMENT_SERVICE = "http://payment-service";

    MockRestServiceServer server;

    PaymentRequest paymentRequest;

    PaymentRequestDto request = new PaymentRequestDto(50L, 1L, 33_000L,
            List.of(new PaymentRequestDto.Item(100L, 2L, 30_000L, 3_000L)));

    PaymentResultDto needsCharge = PaymentResultDto.needsCharge(7L, 33_000L, 13_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(PAYMENT_SERVICE);
        server = MockRestServiceServer.bindTo(builder).build();
        paymentRequest = new PaymentRequest(builder.build());
    }

    @Test
    @DisplayName("결제 서비스가 여는 주문 결제 요청 API(POST /internal/v1/payments/request)로 요청한다")
    void requestPayment_callsPaymentServiceRequestApi() {
        server.expect(requestTo(PAYMENT_SERVICE + "/internal/v1/payments/request"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(needsCharge), MediaType.APPLICATION_JSON));

        paymentRequest.requestPayment(request);

        server.verify();
    }

    @Test
    @DisplayName("결제 요청 정보를 JSON 본문으로 보내고, 200 응답의 결제 결과(NEEDS_CHARGE)를 그대로 돌려준다")
    void requestPayment_sendsJsonAndReturnsResult() {
        server.expect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderGroupId": 50, "buyerId": 1, "totalPrice": 33000,
                         "items": [{"orderId": 100, "sellerId": 2, "winningPrice": 30000, "deliveryFee": 3000}]}
                        """))
                .andRespond(withSuccess(json(needsCharge), MediaType.APPLICATION_JSON));

        PaymentResultDto result = paymentRequest.requestPayment(request);

        assertThat(result).isEqualTo(needsCharge);
        server.verify();
    }

    @Test
    @DisplayName("결제 서비스가 예치금으로 결제했다고(PAID) 응답하면 PAID 결과를 돌려준다")
    void requestPayment_paid() {
        PaymentResultDto paid = PaymentResultDto.paid(7L, 33_000L);
        server.expect(method(HttpMethod.POST))
                .andRespond(withSuccess(json(paid), MediaType.APPLICATION_JSON));

        PaymentResultDto result = paymentRequest.requestPayment(request);

        assertThat(result).isEqualTo(paid);
        assertThat(result.isPaid()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND", "CONFLICT", "INTERNAL_SERVER_ERROR", "SERVICE_UNAVAILABLE"})
    @DisplayName("결제 서비스가 4xx·5xx로 응답하면 PAYMENT_REQUEST_FAILED")
    void requestPayment_errorStatus(HttpStatus status) {
        server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(status));

        assertThatThrownBy(() -> paymentRequest.requestPayment(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_REQUEST_FAILED);
    }

    @Test
    @DisplayName("결제 서비스가 200이 아닌 2xx(202)로 응답하면 본문이 있어도 PAYMENT_REQUEST_FAILED")
    void requestPayment_non200Success() {
        server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json(needsCharge)));

        assertThatThrownBy(() -> paymentRequest.requestPayment(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_REQUEST_FAILED);
    }

    @Test
    @DisplayName("결제 서비스에 연결하지 못하면 PAYMENT_REQUEST_FAILED")
    void requestPayment_connectionFailed() {
        server.expect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> paymentRequest.requestPayment(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_REQUEST_FAILED);
    }

    private String json(PaymentResultDto result) {
        return JsonMapper.builder().build().writeValueAsString(result);
    }
}
