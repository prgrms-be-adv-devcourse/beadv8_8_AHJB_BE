package com.jjinmak.back.boundedContext.auction.out;


import com.jjinmak.back.boundedContext.auction.domain.AuctionMember;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AuctionMemberRepository extends JpaRepository<AuctionMember,Long> {
}
