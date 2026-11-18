package com.jjinmak.back.boundedContext.order.out.repository;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select ci from CartItem ci join fetch ci.seller where ci.winner = :winner order by ci.winAt")
    List<CartItem> findAllByWinnerOrderByWinAt(OrderMember winner);

    @Query("select ci from CartItem ci join fetch ci.seller where ci.winner = :winner and ci.productId in :productIds")
    List<CartItem> findAllByWinnerAndProductIdIn(OrderMember winner, Collection<Long> productIds);

    Optional<CartItem> findByAuctionId(Long auctionId);

    @Query("select ci from CartItem ci join fetch ci.winner join fetch ci.seller where ci.id = :id")
    Optional<CartItem> findByIdWithParticipant(Long id);

    @Query("select ci.id from CartItem ci where ci.paymentDueAt < :now")
    List<Long> findExpiredItemIds(LocalDateTime now);
}
