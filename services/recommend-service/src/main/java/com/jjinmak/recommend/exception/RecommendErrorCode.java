package com.jjinmak.recommend.exception;

import com.jjinmak.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecommendErrorCode implements ErrorCode {

    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}
