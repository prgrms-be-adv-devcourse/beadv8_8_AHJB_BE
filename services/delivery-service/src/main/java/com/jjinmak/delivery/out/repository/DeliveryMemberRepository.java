package com.jjinmak.delivery.out.repository;

import com.jjinmak.delivery.domain.DeliveryMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryMemberRepository extends JpaRepository<DeliveryMember, Long> {
    Optional<DeliveryMember> findByUuid(UUID uuid);
}
