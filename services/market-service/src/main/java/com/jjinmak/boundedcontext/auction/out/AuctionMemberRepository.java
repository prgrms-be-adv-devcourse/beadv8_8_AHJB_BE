package com.jjinmak.boundedcontext.auction.out;


import com.jjinmak.boundedcontext.auction.domain.AuctionMember;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AuctionMemberRepository extends JpaRepository<AuctionMember,Long> {
}
