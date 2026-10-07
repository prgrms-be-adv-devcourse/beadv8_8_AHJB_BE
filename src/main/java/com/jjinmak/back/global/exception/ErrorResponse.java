package com.jjinmak.back.global.exception;

public record ErrorResponse(
        boolean isSuccess,
        String errorCode,
        String message
) {
}
