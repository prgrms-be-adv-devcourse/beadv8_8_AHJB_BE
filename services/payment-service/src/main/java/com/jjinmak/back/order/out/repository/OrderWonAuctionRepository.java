package com.jjinmak.back.order.out.repository;

import com.jjinmak.back.order.domain.OrderWonAuction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderWonAuctionRepository extends JpaRepository<OrderWonAuction, Long> {
}
