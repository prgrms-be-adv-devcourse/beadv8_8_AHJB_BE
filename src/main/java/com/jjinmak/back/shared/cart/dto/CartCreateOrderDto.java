package com.jjinmak.back.shared.cart.dto;

import java.util.List;

public record CartCreateOrderDto(
    Long totalPrice,
    List<CartItemDto> cartItems
) {
}
