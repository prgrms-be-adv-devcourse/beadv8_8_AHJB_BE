package com.jjinmak.back.boundedContext.delivery.in;

import com.jjinmak.back.boundedContext.delivery.app.DeliveryFacade;
import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryResponseDto;
import com.jjinmak.back.boundedContext.delivery.app.dto.DeliveryStatusDto;
import com.jjinmak.back.boundedContext.delivery.in.dto.DeliveryDataDto;
import com.jjinmak.back.global.rsData.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/{orderId}/delivery")
public class DeliveryApiController {

    // TODO: 비즈니스 로직 내에서 사용자 정보 가져오기
    private final UUID userDev = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private final DeliveryFacade deliveryFacade;

    @PutMapping("")
    public ResponseEntity<RsData<DeliveryResponseDto>> updateDeliveryData(@PathVariable Long orderId, @Valid @RequestBody DeliveryDataDto dto){
        DeliveryResponseDto response = deliveryFacade.updateDeliveryData(userDev, orderId, dto.carrier(), dto.waybill());

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("")
    public ResponseEntity<RsData<DeliveryStatusDto>> getDeliveryStatus(@PathVariable Long orderId){
        DeliveryStatusDto response = deliveryFacade.getDeliveryStatus(userDev, orderId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
