package com.jjinmak.back.boundedContext.auction.in;

import com.jjinmak.back.boundedContext.auction.app.AuctionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionApiController {
    private final AuctionFacade auctionFacade;

}
