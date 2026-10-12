package com.jjinmak.back.boundedContext.delivery.in;

import com.jjinmak.back.boundedContext.delivery.app.DeliveryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/order/{orderId}/delivery")
public class DeliveryApiController {

    // TODO: 비즈니스 로직 내에서 사용자 정보 가져오기
    private final UUID sellerDev = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private final DeliveryFacade deliveryFacade;
}
