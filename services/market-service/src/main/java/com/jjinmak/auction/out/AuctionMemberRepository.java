package com.jjinmak.auction.out;


import com.jjinmak.auction.domain.AuctionMember;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AuctionMemberRepository extends JpaRepository<AuctionMember,Long> {
}
