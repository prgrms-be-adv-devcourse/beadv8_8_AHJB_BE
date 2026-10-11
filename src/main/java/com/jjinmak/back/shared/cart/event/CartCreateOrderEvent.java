package com.jjinmak.back.shared.cart.event;

import com.jjinmak.back.shared.cart.dto.CartItemDto;

import java.util.List;

public record CartCreateOrderEvent(
    Long totalPrice,
    List<CartItemDto> cartItems
) {
}
