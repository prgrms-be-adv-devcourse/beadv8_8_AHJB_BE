package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o join fetch o.seller where o.winner = :winner and o.state in :states")
    List<Order> findAllByWinnerAndStateIn(OrderMember winner, Set<OrderState> states);

    @Query("select o from Order o join fetch o.winner where o.seller = :seller and o.state in :states")
    List<Order> findAllBySellerAndStateIn(OrderMember seller, Set<OrderState> states);
}
