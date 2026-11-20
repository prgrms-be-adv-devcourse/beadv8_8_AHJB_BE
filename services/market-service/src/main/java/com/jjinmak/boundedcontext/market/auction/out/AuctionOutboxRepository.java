package com.jjinmak.boundedcontext.market.auction.out;

import com.jjinmak.boundedcontext.market.auction.domain.AuctionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuctionOutboxRepository extends JpaRepository<AuctionOutbox, Long> {

    List<AuctionOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
