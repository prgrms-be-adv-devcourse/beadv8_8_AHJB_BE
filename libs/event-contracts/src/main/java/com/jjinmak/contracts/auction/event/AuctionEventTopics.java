package com.jjinmak.contracts.auction.event;


public final class AuctionEventTopics {

    public static final String AUCTION_STARTED = "auction.started";
    public static final String AUCTION_WON = "auction.won";
    public static final String AUCTION_FAILED = "auction.failed";
    public static final String AUCTION_BID_SUCCEED = "auction.bid-succeed";
    public static final String AUCTION_ENDING_SOON = "auction.ending-soon";
    private AuctionEventTopics() {
    }
}