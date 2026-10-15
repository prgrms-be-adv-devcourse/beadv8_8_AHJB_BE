package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletMemberRepository extends JpaRepository<WalletMember, Long> {
}
