package com.jjinmak.back.boundedContext.cart.out;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
