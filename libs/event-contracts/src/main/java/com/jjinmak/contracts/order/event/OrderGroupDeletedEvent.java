package com.jjinmak.contracts.order.event;

public record OrderGroupDeletedEvent(
      Long orderGroupId,
      long totalPrice
) {
}
