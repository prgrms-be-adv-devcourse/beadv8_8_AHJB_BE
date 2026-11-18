package com.jjinmak.back.shared.auction.event;


public final class AuctionEventTopics {

    public static final String AUCTION_STARTED = "auction.started";
    public static final String AUCTION_WON = "auction.won";
    public static final String AUCTION_FAILED = "auction.failed";

    private AuctionEventTopics() {
    }
}