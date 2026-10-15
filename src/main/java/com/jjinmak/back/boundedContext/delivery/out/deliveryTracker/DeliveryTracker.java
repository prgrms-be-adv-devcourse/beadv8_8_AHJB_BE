package com.jjinmak.back.boundedContext.delivery.out.deliveryTracker;

import com.jjinmak.back.boundedContext.delivery.out.dto.*;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;

import static com.jjinmak.back.boundedContext.delivery.exception.DeliveryErrorCode.*;
import static com.jjinmak.back.boundedContext.delivery.out.dto.DeliveryTrackerExtension.PASS;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTracker {

    private final RestClient deliveryTrackerRestClient;

    // TODO: Webhook을 받을 콜백 주소
    private final String webhookCallbackUrl = "";

    private static final String TRACK_QUERY = """
        query Track($carrierId: ID!, $trackingNumber: String!) {
          track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
            lastEvent {
              time
              status { code }
            }
          }
        }
        """;

    private static final String WEBHOOK_QUERY = """
        mutation RegisterTrackWebhook($input: RegisterTrackWebhookInput!) {
          registerTrackWebhook(input: $input)
        }
        """;

    public DeliveryStatusResponseDto trackLastStatus(CarrierType carrier, String waybill){
        DeliveryStatusRequestDto requestDto = new DeliveryStatusRequestDto(
                TRACK_QUERY,
                new DeliveryStatusRequestDto.Variables(carrier.getCarrierCode(), waybill)
        );

        DeliveryStatusResponseDto response;

        try{
            response = deliveryTrackerRestClient.post()
                    .uri("")
                    .body(requestDto)
                    .exchange((request, res) -> res.bodyTo(DeliveryStatusResponseDto.class));
        } catch (RestClientException e){
            log.error("배송 조회 API 호출 실패", e);
            throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);
        }

        if (response == null)
            throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);

        throwIfError(response);

        if (response.data() == null || response.data().track() == null || response.data().track().lastEvent() == null)
            throw new BusinessException(DELIVERY_TRACKING_NOT_FOUND);

        return response;
    }

    public void registerWebhook(Long deliveryId, Long orderId, CarrierType carrier, String waybill, int expirationDays){
        String webhookCallback = webhookCallbackUrl + '/' + orderId + "/delivery/" + deliveryId;
        WebhookRegisterRequestDto requestDto = new WebhookRegisterRequestDto(
                WEBHOOK_QUERY,
                new WebhookRegisterRequestDto.Variables(new WebhookRegisterRequestDto.Input(carrier.getCarrierCode(), waybill, webhookCallback, OffsetDateTime.now().plusDays(expirationDays)))
        );

        WebhookRegisterResponseDto response;

        try {
            response = deliveryTrackerRestClient.post()
                    .uri("")
                    .body(requestDto)
                    .exchange((request, res) -> res.bodyTo(WebhookRegisterResponseDto.class));
        } catch (RestClientException e){
            log.error("Webhook 등록 API 호출 실패", e);
            throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);
        }

        if (response == null)
            throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);

        throwIfError(response);

        if (response.data() == null || response.data().registerTrackWebhook() == null)
            throw new BusinessException(DELIVERY_TRACKING_NOT_FOUND);
    }

    private void throwIfError(DeliveryStatusResponseDto response){
        if (response.errors() == null || response.errors().isEmpty()){
            return;
        }

        DeliveryStatusResponseDto.GraphQlError error = response.errors().getFirst();
        DeliveryTrackerExtension code = (error.extensions() == null || error.extensions().code() == null)
                ? PASS : error.extensions().code();

        switch (code) {
            case NOT_FOUND -> throw new BusinessException(DELIVERY_TRACKING_NOT_FOUND);
            case BAD_REQUEST -> throw new BusinessException(DELIVERY_TRACKING_INVALID_WAYBILL);
            default -> {
                log.error("배송 조회 API 오류: code=%s, message=%s".formatted(code, error.message()));
                throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);
            }
        }
    }

    private void throwIfError(WebhookRegisterResponseDto response){
        if (response.errors() == null || response.errors().isEmpty()){
            return;
        }

        WebhookRegisterResponseDto.GraphQlError error = response.errors().getFirst();
        DeliveryTrackerExtension code = (error.extensions() == null || error.extensions().code() == null)
                ? PASS : error.extensions().code();

        switch (code){
            case NOT_FOUND -> throw new BusinessException(DELIVERY_TRACKING_NOT_FOUND);
            case BAD_REQUEST -> throw new BusinessException(DELIVERY_TRACKING_INVALID_WAYBILL);
            default -> {
                log.error("Webhook 등록 API 오류: code=%s, message=%s".formatted(code, error.message()));
                throw new BusinessException(DELIVERY_TRACKING_UNAVAILABLE);
            }
        }
    }
}
