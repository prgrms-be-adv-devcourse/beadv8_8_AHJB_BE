package com.jjinmak.back.boundedContext.auction.out;

import com.jjinmak.back.boundedContext.auction.domain.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid,Long> {
}
