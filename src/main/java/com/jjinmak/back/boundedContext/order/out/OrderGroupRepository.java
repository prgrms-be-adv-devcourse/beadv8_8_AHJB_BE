package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderGroupRepository extends JpaRepository<OrderGroup, Long> {
}
