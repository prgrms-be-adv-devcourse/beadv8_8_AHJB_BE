package com.jjinmak.back.boundedContext.delivery.out;

import com.jjinmak.back.boundedContext.delivery.domain.DeliveryMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryMemberRepository extends JpaRepository<DeliveryMember, Long> {
}
