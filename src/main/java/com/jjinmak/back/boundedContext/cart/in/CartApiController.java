package com.jjinmak.back.boundedContext.cart.in;

import com.jjinmak.back.boundedContext.cart.app.CartFacade;
import com.jjinmak.back.global.rsData.RsData;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartApiController {

    private final CartFacade cartFacade;

    @GetMapping("")
    public ResponseEntity<RsData<List<CartItemDto>>> readWinnerItems(){
        // TODO: 인증 방식이 정해진 후에, 접속 유저 정보 가져오기
        List<CartItemDto> response = cartFacade.readWinnerItems(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new RsData<>(response));
    }
}
