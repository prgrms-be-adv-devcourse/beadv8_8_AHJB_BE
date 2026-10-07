package com.jjinmak.back.boundedContext.cart.out;

import com.jjinmak.back.boundedContext.cart.domain.CartItem;
import com.jjinmak.back.boundedContext.cart.domain.CartMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select ci from CartItem ci join fetch ci.seller where ci.winner = :winner order by ci.winAt")
    List<CartItem> findAllByWinnerOrderByWinAt(CartMember winner);
}
