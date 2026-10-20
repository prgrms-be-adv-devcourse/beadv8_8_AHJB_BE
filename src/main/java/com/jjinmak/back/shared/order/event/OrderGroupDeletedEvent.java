package com.jjinmak.back.shared.order.event;

public record OrderGroupDeletedEvent(
      Long orderGroupId,
      long totalPrice
) {
}
