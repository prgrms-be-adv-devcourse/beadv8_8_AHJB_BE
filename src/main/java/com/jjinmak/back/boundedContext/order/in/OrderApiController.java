package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.app.OrderFacade;
import com.jjinmak.back.boundedContext.order.app.dto.OrderDto;
import com.jjinmak.back.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order")
public class OrderApiController {

    private final OrderFacade orderFacade;

    // TODO: 비즈니스 로직 내에서 사용자 정보 가져오기
    private final UUID userDev = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @GetMapping("/winner")
    public ResponseEntity<RsData<List<OrderDto>>> readWinnerOrders(){

        List<OrderDto> response = orderFacade.readWinnerOrders(userDev);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("/seller")
    public ResponseEntity<RsData<List<OrderDto>>> readSellerOrders(){
        List<OrderDto> response = orderFacade.readSellerOrders(userDev);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<RsData<OrderDto>> readOrder(@PathVariable Long orderId){
        OrderDto response = orderFacade.readOrder(userDev, orderId);

        return ResponseEntity.status(HttpStatus.OK).body(new RsData<>(response));
    }
}
