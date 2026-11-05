package com.jjinmak.payment.out;

import com.jjinmak.payment.domain.WalletMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletMemberRepository extends JpaRepository<WalletMember, Long> {
}
