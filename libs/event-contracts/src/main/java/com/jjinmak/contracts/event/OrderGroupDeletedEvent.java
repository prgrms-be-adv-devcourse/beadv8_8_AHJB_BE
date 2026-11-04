package com.jjinmak.contracts.event;

public record OrderGroupDeletedEvent(
      Long orderGroupId,
      long totalPrice
) {
}
