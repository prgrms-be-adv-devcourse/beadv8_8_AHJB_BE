package com.jjinmak.back.boundedContext.wallet.out;

import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletMemberRepository extends JpaRepository<WalletMember, Long> {
}
