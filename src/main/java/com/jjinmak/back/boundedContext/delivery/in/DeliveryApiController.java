package com.jjinmak.back.boundedContext.delivery.in;

import com.jjinmak.back.boundedContext.delivery.app.DeliveryFacade;
import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryResponseDto;
import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryStatusDto;
import com.jjinmak.back.boundedContext.delivery.in.dto.DeliveryDataDto;
import com.jjinmak.back.boundedContext.delivery.in.dto.WebhookCallbackDto;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.shared.delivery.domain.CarrierType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "배송", description = "배송 정보/현황 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/{orderId}/delivery")
public class DeliveryApiController {

    // TODO: 비즈니스 로직 내에서 사용자 정보 가져오기
    private final UUID userDev = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private final DeliveryFacade deliveryFacade;

    @Operation(
            summary = "배송 정보 수정",
            description = "기존에 등록된 배송 정보를 수정합니다."
    )
    @PutMapping("")
    public ResponseEntity<RsData<DeliveryResponseDto>> updateDeliveryData(@PathVariable Long orderId, @Valid @RequestBody DeliveryDataDto dto){
        DeliveryResponseDto response = deliveryFacade.updateDeliveryData(userDev, orderId, dto.carrier(), dto.waybill());

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "배송 현황 조회",
            description = "주문 id에 기반하여 배송 현황을 조회합니다."
    )
    @GetMapping("")
    public ResponseEntity<RsData<DeliveryStatusDto>> getDeliveryStatus(@PathVariable Long orderId){
        DeliveryStatusDto response = deliveryFacade.getDeliveryStatus(userDev, orderId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @Operation(
            summary = "Delivery Tracker Webhook 콜백",
            description = "등록된 Webhook으로 콜백 요청을 수신합니다."
    )
    @PostMapping("/{deliveryId}")
    public ResponseEntity<RsData<Void>> webhookCallback(@PathVariable Long deliveryId, @PathVariable Long orderId, @Valid @RequestBody WebhookCallbackDto dto){
        CarrierType carrier = CarrierType.fromCode(dto.carrierId());
        deliveryFacade.webhookCallback(deliveryId, orderId, carrier, dto.trackingNumber());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RsData<>(null));
    }
}
