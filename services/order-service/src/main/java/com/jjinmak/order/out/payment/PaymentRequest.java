package com.jjinmak.order.out.payment;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.jjinmak.order.exception.OrderErrorCode.PAYMENT_REQUEST_FAILED;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequest {

    private final RestClient paymentRequestClient;

    public PaymentResultDto requestPayment(PaymentRequestDto dto){

        ResponseEntity<PaymentResultDto> result;

        try {
            result = paymentRequestClient.post()
                    .uri("/internal/v1/payments/request")
                    .body(dto)
                    .retrieve()
                    .toEntity(PaymentResultDto.class);
        } catch (RestClientException e) {
            log.error("결제 요청 실패", e);
            throw new BusinessException(PAYMENT_REQUEST_FAILED);
        }

        if (result.getStatusCode() == HttpStatus.OK){
            return result.getBody();
        }
        else {
            throw new BusinessException(PAYMENT_REQUEST_FAILED);
        }
    }
}
