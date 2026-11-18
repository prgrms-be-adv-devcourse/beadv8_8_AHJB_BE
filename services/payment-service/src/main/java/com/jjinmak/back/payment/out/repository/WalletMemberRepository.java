package com.jjinmak.back.payment.out.repository;

import com.jjinmak.back.payment.domain.WalletMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletMemberRepository extends JpaRepository<WalletMember, Long> {
}
