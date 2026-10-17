package com.jjinmak.back.boundedContext.order.out.repository;

import com.jjinmak.back.boundedContext.order.domain.CartMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartMemberRepository extends JpaRepository<CartMember, Long> {
    Optional<CartMember> findByUuid(UUID uuid);
}
