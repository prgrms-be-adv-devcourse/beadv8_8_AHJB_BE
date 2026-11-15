package com.jjinmak.recommend.exception;

import com.jjinmak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Getter
@RequiredArgsConstructor
public enum RecommendErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND("recommend001", "상품이 존재하지 않습니다.", NOT_FOUND),
    AUCTION_NOT_FOUND("recommend002", "경매가 존재하지 않습니다.", NOT_FOUND),
    HASH_FAIL("recommend003", "해싱에 실패했습니다.", BAD_REQUEST),
    EMBEDDING_NOT_FOUND("recommend004", "임베딩이 존재하지 않습니다.", NOT_FOUND),
    SEARCH_TOO_MANY_REQUESTS("recommend005", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", TOO_MANY_REQUESTS)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
