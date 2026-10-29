package com.jjinmak.back.boundedContext.settlement.domain;

import com.jjinmak.back.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {
    SETTLEMENT_NOT_FOUND("SETTLEMENT001", "존재하지 않는 정산서", HttpStatus.NOT_FOUND),
    NOT_SETTLEMENT_SELLER("SETTLEMENT002", "판매자 본인만 조회 가능", HttpStatus.FORBIDDEN),
    ALREADY_COMPLETED("SETTLEMENT003", "이미 지급된 정산서", HttpStatus.CONFLICT)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
