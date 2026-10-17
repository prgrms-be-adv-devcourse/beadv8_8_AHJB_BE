package com.jjinmak.back.boundedContext.order.out.repository;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query(
            value = "select o from Order o join fetch o.seller where o.winner = :winner and o.state in :states order by o.createdAt desc",
            countQuery = "select count(o) from Order o where o.winner = :winner and o.state in :states"
    )
    Page<Order> findAllByWinnerAndStateIn(OrderMember winner, Set<OrderState> states, Pageable pageable);

    @Query(
            value = "select o from Order o join fetch o.winner where o.seller = :seller and o.state in :states order by o.createdAt desc",
            countQuery = "select count(o) from Order o where o.seller = :seller and o.state in :states"
    )
    Page<Order> findAllBySellerAndStateIn(OrderMember seller, Set<OrderState> states, Pageable pageable);

    @Query("select o from Order o join fetch o.winner join fetch o.seller where o.id = :orderId and o.state in :states")
    Optional<Order> findByIdAndStateInWithWinnerAndSeller(Long orderId, Set<OrderState> states);

    @Query("select o from Order o join fetch o.winner join fetch o.seller where o.id = :orderId")
    Optional<Order> findByIdWithWinnerAndSeller(Long orderId);

    @Query("select o.id from Order o where o.state = OrderState.SHIPPED and o.confirmExpiredAt < :now")
    List<Long> findExpiredOrderIds(LocalDateTime now);

    @Query("select o from Order o where o.state = OrderState.WAITING and o.createdAt < :base")
    List<Order> findOverWaitedAll(LocalDateTime base);
}
