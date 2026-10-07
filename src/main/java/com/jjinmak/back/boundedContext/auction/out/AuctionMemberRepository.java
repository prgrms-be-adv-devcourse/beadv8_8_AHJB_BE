package com.jjinmak.back.boundedContext.auction.out;


import com.jjinmak.back.boundedContext.auction.domain.AuctionMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuctionMemberRepository extends JpaRepository<AuctionMember,Long> {
   //uuid로 회원찾기
    Optional<AuctionMember> findByUuid(UUID uuid);
}
