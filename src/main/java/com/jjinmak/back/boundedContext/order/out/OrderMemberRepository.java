package com.jjinmak.back.boundedContext.order.out;

import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderMemberRepository extends JpaRepository<OrderMember, Long> {
    Optional<OrderMember> findByUuid(UUID uuid);
}
