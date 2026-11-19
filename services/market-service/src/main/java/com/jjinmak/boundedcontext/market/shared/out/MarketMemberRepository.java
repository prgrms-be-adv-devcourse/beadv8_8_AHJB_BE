package com.jjinmak.boundedcontext.market.shared.out;

import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketMemberRepository extends JpaRepository<MarketMember, Long> {
}
