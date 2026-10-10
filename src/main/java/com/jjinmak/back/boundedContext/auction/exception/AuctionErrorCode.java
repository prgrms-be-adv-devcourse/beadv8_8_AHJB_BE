package com.jjinmak.back.boundedContext.auction.exception;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuctionErrorCode implements ErrorCode {
    AUCTION_NOT_FOUND("AUCTION001", "존재하지 않는 경매입니다.", HttpStatus.NOT_FOUND),
    AUCTION_NOT_IN_PROGRESS("AUCTION002", "진행중인 경매가 아닙니다.", HttpStatus.BAD_REQUEST),
    AUCTION_ALREADY_ENDED("AUCTION003", "이미 종료된 경매입니다.", HttpStatus.BAD_REQUEST),
    SELLER_CANNOT_BID("AUCTION004", "판매자는 자신의 경매에 입찰할 수 없습니다.", HttpStatus.FORBIDDEN),
    ALREADY_HIGHEST_BIDDER("AUCTION005", "현재 최고 입찰자는 다시 입찰할 수 없습니다.", HttpStatus.BAD_REQUEST),
    BID_PRICE_TOO_LOW("AUCTION006", "입찰가가 최소 입찰가보다 낮습니다.", HttpStatus.BAD_REQUEST),
    INVALID_BID_UNIT("AUCTION007", "입찰가가 입찰 단위에 맞지 않습니다.", HttpStatus.BAD_REQUEST),
    BID_CONFLICT("AUCTION008", "다른 입찰이 먼저 처리되었습니다. 다시 입찰해 주세요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
